package com.unibo.handy.service

import com.unibo.handy.R
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.JsonParser
import com.unibo.handy.HandyApp
import com.unibo.handy.data.network.MessageDispatcher
import com.unibo.handy.data.network.WebSocketManager
import com.unibo.handy.data.repository.UserRepository
import com.unibo.handy.domain.usecase.profile.SendHeartbeatUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
// Funge da pattern FACADE
class NetworkService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    // Serve a verificare se il servizio è già in esecuzione e impedire di creare duplicati
    private var backgroundJob: Job? = null
    private var heartbeatJob: Job? = null

    // Dipendenze
    @Inject
    lateinit var userRepo: UserRepository
    @Inject
    lateinit var sendHeartbeatUseCase: SendHeartbeatUseCase
    @Inject
    lateinit var dispatcher: MessageDispatcher
    @Inject
    lateinit var webSocketManager: WebSocketManager

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Configurazione notifica Foreground
        val notification = NotificationCompat.Builder(this, HandyApp.CHANNEL_ID)
            .setContentTitle("Handy Background")
            .setContentText("Ricerca in corso...")
            .setSmallIcon(R.drawable.handy_icon)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } catch (e: Exception) {
            Log.e("HandyService", "Foreground start error: ${e.message}")
            stopSelf()
        }

        startBackgroundLogic()
        return START_STICKY
    }

    private fun startBackgroundLogic() {
        backgroundJob?.cancel()

        backgroundJob = scope.launch {
            userRepo.currentUserFlow.collectLatest { user ->

                // CASO LOGOUT
                if (user == null) {
                    Log.d("HandyService", "Logout: Stop dispatcher and colse socket")
                    //dispatcher.stopDispatching()
                    webSocketManager.close()
                    WorkManager.getInstance(applicationContext).cancelUniqueWork("HeartbeatWork")
                    return@collectLatest
                }

                // 1. Connessione WebSocket (Stateful)
                launch {
                    try {
                        webSocketManager.connect(user.userId)
                    } catch (e: Exception) {
                        Log.e("HandyService", "WebSocket connection error", e)
                    }
                }

                // 2. Ascolto del WebSocket e invio al Dispatcher
                launch {
                    webSocketManager.incomingMessages.collectLatest { rawMessage ->
                        try {
                            // 1. Parsing preliminare solo per ottenere "type" e "payload" come JsonElement
                            val root = JsonParser.parseString(rawMessage).asJsonObject
                            val action = root.get("type")?.asString
                            val payload = root.get("payload")?.toString()

                            if (action != null && payload != null) {
                                dispatcher.dispatch(action, payload)
                            }
                        } catch (e: Exception) {
                            Log.e("HandyService", "Errore parsing messaggio socket", e)
                        }
                    }
                }

                // 3. WORKMANAGER HEARTBEAT
                if (user.helpModeActive) {
                    // --- STRATEGIA 1: BACKGROUND (WorkManager a 15 min) ---
                    // Configura il WorkManager per eseguire l'heartbeat solo se c'è connessione internet
                    val constraints = Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()

                    val heartbeatRequest = PeriodicWorkRequestBuilder<HeartbeatWorker>(
                        15,
                        TimeUnit.MINUTES
                    )
                        .setConstraints(constraints)
                        .setInitialDelay(15, TimeUnit.MINUTES)
                        .build()

                    // Se c'è già un worker con lo stesso nome, UPDATE lo sovrascrive con le nuove impostazioni
                    WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                        "HeartbeatWork",
                        ExistingPeriodicWorkPolicy.UPDATE,
                        heartbeatRequest
                    )

                    // --- STRATEGIA 2: FOREGROUND (Coroutine a 5 min) ---
                    // Questo gira solo finché l'app/servizio è vivo
                    heartbeatJob?.cancel()
                    heartbeatJob = launch {
                        while (isActive) {
                            sendHeartbeatUseCase()

                            delay(10 * 1000L) //5 * 60 * 1000L
                        }
                    }
                } else {
                    WorkManager.getInstance(applicationContext).cancelUniqueWork("HeartbeatWork")
                    heartbeatJob?.cancel()
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        webSocketManager.close()
        WorkManager.getInstance(applicationContext).cancelUniqueWork("HeartbeatWork")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
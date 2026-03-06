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
import com.unibo.handy.data.repository.ChatRepository
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

/**
 * Cuore dell'elaborazione in background dell'app.
 * Implementa il pattern Facade per nascondere la complessità della rete (WebSocket)
 * e della schedulazione (WorkManager + Coroutines) al resto dell'applicazione.
 */
@AndroidEntryPoint
class NetworkService : Service() {
    // Scope legato al ciclo di vita del Servizio, indipendente dalla UI
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var backgroundJob: Job? = null
    private var heartbeatJob: Job? = null

    @Inject
    lateinit var userRepo: UserRepository
    @Inject
    lateinit var chatRepo: ChatRepository
    @Inject
    lateinit var sendHeartbeatUseCase: SendHeartbeatUseCase
    @Inject
    lateinit var dispatcher: MessageDispatcher
    @Inject
    lateinit var webSocketManager: WebSocketManager

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Configurazione della notifica persistente
        val notification = NotificationCompat.Builder(this, HandyApp.CHANNEL_ID)
            .setContentTitle("Handy Background")
            .setContentText("Ricerca in corso...")
            .setSmallIcon(R.drawable.handy_icon)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
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

        // START_STICKY istruisce l'OS a riavviare il servizio se viene ucciso per mancanza di RAM
        return START_STICKY
    }

    private fun startBackgroundLogic() {
        backgroundJob?.cancel()

        backgroundJob = scope.launch {
            // Reagisce dinamicamente ai cambiamenti di stato dell'utente
            userRepo.currentUserFlow.collectLatest { user ->

                if (user == null) {
                    webSocketManager.close()
                    WorkManager.getInstance(applicationContext).cancelUniqueWork("HeartbeatWork")
                    return@collectLatest
                }

                // 1. Connessione WebSocket (Stateful)
                launch {
                    try {
                        webSocketManager.connect(user.userId)
                        chatRepo.syncPendingMessages()
                    } catch (e: Exception) {
                        Log.e("HandyService", "WebSocket connection error", e)
                    }
                }

                // 2. Ascolto del WebSocket e instradamento messaggi
                launch {
                    webSocketManager.incomingMessages.collectLatest { rawMessage ->
                        try {
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

                // 3. GESTIONE HEARTBEAT (Invio posizione offuscata)
                if (user.helpModeActive) {
                    // --- STRATEGIA 1: BACKGROUND (WorkManager) ---
                    val constraints = Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()

                    val heartbeatRequest = PeriodicWorkRequestBuilder<HeartbeatWorker>(
                        15,
                        TimeUnit.MINUTES // Limite minimo imposto da Android
                    )
                        .setConstraints(constraints)
                        .setInitialDelay(15, TimeUnit.MINUTES)
                        .build()

                    WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                        "HeartbeatWork",
                        ExistingPeriodicWorkPolicy.UPDATE,
                        heartbeatRequest
                    )

                    // --- STRATEGIA 2: FOREGROUND ATTIVO (Coroutine) ---
                    heartbeatJob?.cancel()
                    heartbeatJob = launch {
                        while (isActive) {
                            sendHeartbeatUseCase()

                            delay(10 * 1000L) //5 * 60 * 1000L
                        }
                    }
                } else {
                    // Spegnimento reattivo se l'utente disattiva la modalità Helper
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
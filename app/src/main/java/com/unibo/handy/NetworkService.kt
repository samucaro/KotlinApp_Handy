package com.unibo.handy

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
import com.unibo.handy.data.network.HeartbeatWorker
import com.unibo.handy.data.network.MessageDispatcher
import com.unibo.handy.data.network.WebSocketManager
import com.unibo.handy.data.repository.LocationRepository
import com.unibo.handy.data.repository.UserRepository
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
    @Inject lateinit var userRepo: UserRepository
    @Inject lateinit var locationRepo: LocationRepository
    @Inject lateinit var dispatcher: MessageDispatcher
    @Inject lateinit var webSocketManager: WebSocketManager

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("HandyService", "Service Started")

        // Configurazione notifica Foreground
        val notification = NotificationCompat.Builder(this, HandyApp.CHANNEL_ID)
            .setContentTitle("Handy Background")
            .setContentText("Ricerca in corso...")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
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
        // Cancella eventuali job precedenti per evitare duplicati
        backgroundJob?.cancel()

        backgroundJob = scope.launch {
            // Osserva l'utente corrente
            userRepo.currentUserFlow.collectLatest { user ->

                // CASO LOGOUT
                if (user == null) {
                    Log.d("HandyService", "Logout: Stop dispatcher and colse socket")
                    //dispatcher.stopDispatching()
                    webSocketManager.close()
                    WorkManager.getInstance(applicationContext).cancelUniqueWork("HeartbeatWork")
                    return@collectLatest
                }

                Log.d("HandyService", "User active: ${user.userId}. Start services.")

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
                                // Passa il messaggio al dispatcher
                                dispatcher.dispatch(action, payload)
                            }
                        } catch (e: Exception) {
                            Log.e("HandyService", "Errore parsing messaggio socket", e)
                        }
                    }
                }


                // 3. WORKMANAGER HEARTBEAT (Stateless - Solo se helper Mode)
                if (user.helpModeActive) {
                    Log.d("HandyService", "Helper mode ON: Start Dual Rate Strategy")

                    // --- STRATEGIA 1: BACKGROUND (WorkManager a 15 min) ---
                    // Configura il WorkManager per eseguire l'heartbeat solo se c'è connessione internet
                    val constraints = Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()

                    // Schedulazione a 15 minuti (limite minimo di Android)
                    val heartbeatRequest = PeriodicWorkRequestBuilder<HeartbeatWorker>(15, TimeUnit.MINUTES)
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
                        Log.d("HandyService", "Avvio ciclo heartbeat rapido (5 min)")
                        while (isActive) {
                            // Inviamo la posizione
                            locationRepo.sendHeartbeat()

                            // Aspettiamo 5 minuti (300.000 millisecondi)
                            // Non scendere sotto i 3-5 minuti per rispettare i vincoli del paper SamaritanCloud
                            delay(10 * 1000L) //5 * 60 * 1000L
                        }
                    }
                } else {
                    // SE L'UTENTE SPEGNE L'HELPER MODE, CANCELLIAMO IL WORKER
                    Log.d("HandyService", "Helper mode OFF: Cancel WorkManager")
                    WorkManager.getInstance(applicationContext).cancelUniqueWork("HeartbeatWork")
                    heartbeatJob?.cancel()
                }
            }
        }
    }

    override fun onDestroy() {
        Log.w("HandyService", "Service Destroyed")
        //dispatcher.stopDispatching()
        scope.cancel()
        webSocketManager.close()
        WorkManager.getInstance(applicationContext).cancelUniqueWork("HeartbeatWork")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
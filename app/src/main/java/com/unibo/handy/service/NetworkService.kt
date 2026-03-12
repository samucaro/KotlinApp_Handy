package com.unibo.handy.service

import android.app.AlarmManager
import android.app.PendingIntent
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    private var connectionJob: Job? = null
    private var listenerJob: Job? = null

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

    companion object {
        const val ACTION_HEARTBEAT_TICK = "com.unibo.handy.ACTION_HEARTBEAT"
    }

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
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } catch (e: Exception) {
            Log.e("HandyService", "Errore avvio Foreground: ${e.message}")
            stopSelf()
        }

        when (intent?.action) {
            ACTION_HEARTBEAT_TICK -> {
                // L'allarme ha suonato: esegui l'invio
                performHeartbeat()
            }
            else -> {
                // Avvio normale o riavvio dopo kill
                startBackgroundLogic() // Mantiene WebSocket
                scheduleNextAlarm()    // Fa partire il primo allarme
            }
        }

        // START_STICKY istruisce l'OS a riavviare il servizio se viene ucciso per mancanza di RAM
        return START_STICKY
    }

    private fun startBackgroundLogic() {
        backgroundJob?.cancel()

        backgroundJob = scope.launch {

            // BLOCCO 1: GESTIONE WEBSOCKET
            launch {
                userRepo.currentUserFlow
                    // Estrae SOLO l'ID, così la connessione non cade se cambia la modalità Helper
                    .map { it?.userId }
                    .distinctUntilChanged()
                    .collectLatest { userId ->

                        if (userId == null) {
                            webSocketManager.close()
                            connectionJob?.cancel()
                            listenerJob?.cancel()
                            return@collectLatest
                        }

                        connectionJob?.cancel()
                        listenerJob?.cancel()

                        // 1. Connessione WebSocket
                        connectionJob = launch {
                            try {
                                webSocketManager.connect(userId)
                                chatRepo.syncPendingMessages()
                            } catch (e: Exception) {
                                Log.e("HandyService", "WebSocket connection error", e)
                            }
                        }

                        // 2. Ascolto del WebSocket
                        listenerJob = launch {
                            webSocketManager.incomingMessages.collect { rawMessage ->
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
                    }
            }

            // BLOCCO 2: GESTIONE HEARTBEAT
            launch {
                userRepo.currentUserFlow.collectLatest { user ->

                    if (user == null || !user.helpModeActive) {
                        // Spegnimento reattivo se l'utente disattiva la modalità Helper o fa logout
                        WorkManager.getInstance(applicationContext).cancelUniqueWork("HeartbeatWork")
                        heartbeatJob?.cancel()
                        return@collectLatest
                    }

                    // --- STRATEGIA 1: BACKGROUND (WorkManager) ---
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

                    WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                        "HeartbeatWork",
                        ExistingPeriodicWorkPolicy.UPDATE,
                        heartbeatRequest
                    )

                    // --- STRATEGIA 2: FOREGROUND ATTIVO (Coroutine) ---
                    heartbeatJob?.cancel()
                    heartbeatJob = launch {

                        delay(3000)

                        while (isActive) {
                            try {
                                sendHeartbeatUseCase()
                            } catch (e: Exception) {
                                Log.e("HandyService", "Errore Heartbeat: ${e.message}")
                            }

                            delay(10 * 1000L)
                        }
                    }
                }
            }
        }
    }

    private fun performHeartbeat() {
        scope.launch {
            try {
                sendHeartbeatUseCase()
                Log.i("HandyService", "Heartbeat 5min inviato con successo")
            } catch (e: Exception) {
                Log.e("HandyService", "Errore battito: ${e.message}")
            } finally {
                scheduleNextAlarm()
            }
        }
    }

    private fun scheduleNextAlarm() {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, NetworkService::class.java).apply {
            action = ACTION_HEARTBEAT_TICK
        }

        val pendingIntent = PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Sveglia la CPU ogni 5 minuti anche in Doze Mode
        val triggerAt = System.currentTimeMillis() + (5 * 60 * 1000L)

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent
        )
    }

    override fun onDestroy() {
        scope.cancel()
        webSocketManager.close()
        WorkManager.getInstance(applicationContext).cancelUniqueWork("HeartbeatWork")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
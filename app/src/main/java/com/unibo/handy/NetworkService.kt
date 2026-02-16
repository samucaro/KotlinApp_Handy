package com.unibo.handy

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.unibo.handy.data.network.MessageDispatcher
import com.unibo.handy.data.network.WebSocketManager
import com.unibo.handy.data.repository.LocationRepository
import com.unibo.handy.data.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// Funge da pattern FACADE
class NetworkService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    // Serve a verificare se il servizio è già in esecuzione e impedire di creare duplicati
    private var backgroundJob: Job? = null

    // Dipendenze
    private lateinit var userRepo: UserRepository
    private lateinit var locationRepo: LocationRepository
    private lateinit var dispatcher: MessageDispatcher
    private lateinit var webSocketManager: WebSocketManager

    override fun onCreate() {
        super.onCreate()
        val app = applicationContext as HandyApp

        // INIEZIONE DIPENDENZE
        userRepo = app.userRepository
        locationRepo = app.locationRepository
        dispatcher = app.realtimeDispatcher
        webSocketManager = app.webSocketManager
    }

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
                    dispatcher.stopDispatching()
                    webSocketManager.close()
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

                // 2. Avvio dispatcher
                dispatcher.startDispatching()


                // 3. LOOP HEARTBEAT (Stateless - Solo se helper Mode)
                if(user.helpModeActive) {
                    Log.d("HandyService", "Helper mode: Start heartbeat")
                    // Lancia una coroutine figlia che vive finché `collectLatest` non ricomincia
                    launch {
                        while (isActive) {
                            locationRepo.sendHeartbeat()
                            delay(30_000)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        Log.w("HandyService", "Service Destroyed")
        dispatcher.stopDispatching()
        scope.cancel()
        webSocketManager.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
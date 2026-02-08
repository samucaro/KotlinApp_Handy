package com.unibo.handy

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
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

class NetworkService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    // Serve a verificare se il servizio è già in esecuzione e impedire di creare duplicati
    private var backgroundJob: Job? = null
    private lateinit var repository: UserRepository

    override fun onCreate() {
        super.onCreate()
        val app = applicationContext as HandyApp //cast
        repository = app.userRepository
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("HandyService", "Service Started/Resumed")

        val notification = NotificationCompat.Builder(this, HandyApp.CHANNEL_ID)
            .setContentTitle("Handy Attivo")
            .setContentText("Ricerca in corso...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC // o FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } catch (e: Exception) {
            Log.e("HandyService", "CRASH startForeground: ${e.message}")
            stopSelf()
        }

        // 2. Avvia la logica (Pattern "Idempotente": se è già attivo, resetta o ignora)
        startBackgroundLogic()

        return START_STICKY
    }

    private fun startBackgroundLogic() {
        backgroundJob?.cancel()

        backgroundJob = serviceScope.launch {
            repository.currentUserFlow.collectLatest { user ->
                if (user == null) {
                    Log.d("HandyService", "Nessun utente loggato. Metto in pausa.")
                    return@collectLatest
                }

                Log.d("HandyService", "Utente attivo: ${user.userId}. Avvio loop.")

                // Connessione WebSocket
                launch {
                    try {
                        repository.ensureWebSocketConnection()
                    } catch (e: Exception) {
                        Log.e("HandyService", "Errore WS", e)
                    }
                }

                // LOOP HEARTBEAT
                // Invia la posizione criptata ogni 30 secondi se l'utente è un Helper
                launch {
                    while (isActive) {
                        if (user.helpModeActive) {
                            Log.v("HandyService", "Invio Heartbeat periodico...")
                            repository.sendHeartbeat()
                        }
                        delay(30_000)
                    }
                }
            }
        }
    }

    /*private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "HANDY_CHANNEL")
            .setContentTitle("Handy attivo")
            .setContentText("Ricerca match e connessione attivi in background")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "HANDY_CHANNEL",
            "Background Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }*/

    override fun onDestroy() {
        Log.w("HandyService", "Service Destroyed")
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
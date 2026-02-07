package com.unibo.handy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.unibo.handy.data.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NetworkService : Service() {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repository: UserRepository

    override fun onCreate() {
        super.onCreate()
        val app = applicationContext as HandyApp
        repository = app.userRepository
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.e("HandyDEBUG", "SERVICE AVVIATO: onStartCommand chiamato!")

        createNotificationChannel()

        val notification = createNotification()
        try {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } catch (e: Exception) {
            Log.e("HandyDEBUG", "Errore startForeground: ${e.message}")
        }

        coroutineScope.launch {
            repository.currentUserFlow.collect { user ->
                if (user != null) {
                    repository.ensureWebSocketConnection()
                }
            }
        }

        return START_STICKY
    }

    private fun createNotification(): Notification {
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
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
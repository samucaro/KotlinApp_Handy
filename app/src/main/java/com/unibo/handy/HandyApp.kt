package com.unibo.handy

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Entry point dell'applicazione Android.
 * Viene istanziata dal sistema operativo prima di qualsiasi Activity o Service.
 */
@HiltAndroidApp
class HandyApp : Application(), Configuration.Provider {
    // Inietta la Factory di Hilt per permettere la Dependency Injection nei Worker
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    // Costanti statiche per l'intero ciclo di vita dell'app
    companion object {
        const val CHANNEL_ID = "handy_service_channel"
        const val CHANNEL_NAME = "Handy Background Service"
    }

    override fun onCreate() {
        super.onCreate()
        // Inizializzazione delle configurazioni di sistema necessarie fin dall'avvio
        createNotificationChannel()
    }

    /**
     * Da Android 8.0 (API 26), i Foreground Service richiedono obbligatoriamente
     * un Notification Channel registrato a livello di sistema.
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mantains service running in background"
        }

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    /**
     * Delega a Hilt la creazione dei WorkManager (es. HeartbeatWorker).
     * Senza questo, il sistema operativo non saprebbe come "iniettare" gli UseCase nel Worker.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
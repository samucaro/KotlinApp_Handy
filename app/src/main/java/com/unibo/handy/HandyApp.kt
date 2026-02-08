package com.unibo.handy

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.unibo.handy.data.db.HandyDB
import com.unibo.handy.data.repository.UserRepository
import com.unibo.handy.data.repository.ChatRepository
import com.unibo.handy.data.LocationClientSensor
import com.unibo.handy.data.network.RetrofitClient
import com.unibo.handy.data.network.WebSocketManager
import com.unibo.handy.domain.MatchingService


class HandyApp : Application() {
    // PATTERN SINGLETON: Permette al Service di accedere ai Repository
    companion object {
        // ID del canale per la notifica persistente
        const val CHANNEL_ID = "handy_service_channel"
        const val CHANNEL_NAME = "Handy Background Service"
    }

    // Valutare utilizzo di Hilt per injectare le dipendenze
    // DataBase (lazy serve a inizializzare il DB solo quando viene usato)
    val db by lazy { HandyDB.getDatabase(this) }
    // Sensori e Rete
    private val locationClient by lazy { LocationClientSensor(this) }
    private val webSocketManager by lazy { WebSocketManager(RetrofitClient.sharedHttpClient) }
    // Dominio e Dati
    private val matchingService by lazy {
        MatchingService(db.storedClientDao())
    }

    val chatRepository by lazy {
        ChatRepository(db.chatDao(), db.userDao(), webSocketManager)
    }
    val userRepository by lazy {
        UserRepository(
            chatRepository = chatRepository,
            userDao = db.userDao(),
            storedClientDao = db.storedClientDao(),
            matchDao = db.matchDao(),
            webSocketManager = webSocketManager,
            apiService = RetrofitClient.retrofitService,
            locationClient = locationClient,
            matchingService = matchingService
        )
    }

    override fun onCreate() {
        super.onCreate()

        // Crea il canale di notifica
        createNotificationChannel()
    }

    // Funzione di utilità per configurare il canale delle notifiche
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mantiene attivo il servizio per il matching in background"
        }

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}
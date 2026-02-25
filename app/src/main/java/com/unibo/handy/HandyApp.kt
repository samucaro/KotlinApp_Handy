package com.unibo.handy

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.gson.Gson
import com.unibo.handy.data.db.HandyDB
import com.unibo.handy.data.repository.UserRepository
import com.unibo.handy.data.repository.ChatRepository
import com.unibo.handy.data.repository.LocationRepository
import com.unibo.handy.data.repository.MatchingRepository
import com.unibo.handy.data.repository.SecureKeyRepository
import com.unibo.handy.data.network.MessageDispatcher
import com.unibo.handy.data.LocationClientSensor
import com.unibo.handy.domain.CryptoManager
import com.unibo.handy.data.network.WebSocketManager
import com.unibo.handy.data.repository.strategy.ChatMessageStrategy
import com.unibo.handy.data.repository.strategy.ComputeMatchStrategy
import com.unibo.handy.data.repository.strategy.StoreProfileStrategy
import com.unibo.handy.domain.MatchingService
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HandyApp : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    // PATTERN SINGLETON: Permette al Service di accedere ai Repository
    companion object {
        // ID del canale per la notifica persistente
        const val CHANNEL_ID = "handy_service_channel"
        const val CHANNEL_NAME = "Handy Background Service"
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
            description = "Mantains service running in background"
        }

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    // Configura il WorkManager per usare Hilt
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    /*
    val gson = Gson()

    // Valutare utilizzo di Hilt per injectare le dipendenze
    // DataBase (lazy serve a inizializzare il DB solo quando viene usato)
    val db by lazy { HandyDB.getDatabase(this) }
    // Componenti core
    private val locationClient by lazy { LocationClientSensor(this) }
    val webSocketManager by lazy { WebSocketManager(RetrofitClient.sharedHttpClient) }
    // Servizio di dominio
    private val matchingService by lazy {
        MatchingService(db.storedClientDao())
    }

    // --- SECURITY COMPONENTS ---
    // Instanziamento del CryptoManager (che parla con il Keystore di Android)
    val cryptoManager by lazy { CryptoManager() }

    // --- 1. REPOSITORIES ---
    val locationRepository by lazy {
        LocationRepository(
            locationClient = locationClient,
            apiService = RetrofitClient.retrofitService,
            userDao = db.userDao()
        )
    }

    val userRepository by lazy {
        UserRepository(
            userDao = db.userDao(),
            apiService = RetrofitClient.retrofitService,
            locationRepo = locationRepository
        )
    }

    val matchingRepository by lazy{
        MatchingRepository(
            webSocketManager = webSocketManager,
            matchDao = db.matchDao(),
            storedClientDao = db.storedClientDao(),
            matchingService = matchingService,
            secureKeyRepository = secureKeyRepository
        )
    }

    val chatRepository by lazy {
        ChatRepository(
            db.chatDao(),
            db.userDao(),
            db.matchDao(),
            webSocketManager
        )
    }

    val secureKeyRepository by lazy {
        SecureKeyRepository(this, cryptoManager)
    }

    // --- 2. STRATEGIES ---
    private val computeMatchHandler by lazy {
        ComputeMatchStrategy(matchingRepository, gson)
    }

    private val storeProfileHandler by lazy {
        StoreProfileStrategy(matchingRepository, gson)
    }

    private val chatHandler by lazy {
        ChatMessageStrategy(chatRepository, gson)
    }

    // --- 3. DISPATCHER CONFIGURATION ---
    private val messageHandlersMap by lazy {
        mapOf(
            "COMPUTE_MATCH" to computeMatchHandler,
            "STORE_PROFILE" to storeProfileHandler,
            "UPDATE_PROFILE" to storeProfileHandler,
            "CHAT_MESSAGE" to chatHandler
        )
    }

    // Iniettiamo la mappa nel Dispatcher
    val realtimeDispatcher by lazy {
        MessageDispatcher(
            handlers = messageHandlersMap
        )
    }*/
}
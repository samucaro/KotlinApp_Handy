package com.unibo.handy

import android.app.Application
import com.unibo.handy.data.db.HandyDB
import com.unibo.handy.data.repository.UserRepository
import com.unibo.handy.data.repository.ChatRepository
import com.unibo.handy.data.LocationClientSensor
import com.unibo.handy.data.network.RetrofitClient
import com.unibo.handy.data.network.WebSocketManager
import com.unibo.handy.domain.MatchingService


class HandyApp : Application() {
    // DataBase
    val db by lazy { HandyDB.getDatabase(this) }
    // Sensore posizione
    private val locationClient by lazy { LocationClientSensor(this) }
    // WebSocket Manager
    private val webSocketManager by lazy { WebSocketManager(RetrofitClient.sharedHttpClient) }
    // Matching Service
    private val matchingService by lazy {
        MatchingService(db.storedClientDao())
    }

    val chatRepository by lazy {
        ChatRepository(
            chatDao = db.chatDao(),
            userDao = db.userDao(),
            webSocketManager = webSocketManager
        )
    }
    val userRepository by lazy {
        UserRepository(
            chatRepository = chatRepository,
            userDao = db.userDao(),
            storedClientDao = db.storedClientDao(),
            matchDao = db.matchDao(),
            webSocketManager = webSocketManager,
            // API Service (Retrofit client)
            apiService = RetrofitClient.retrofitService,
            locationClient = locationClient,
            matchingService = matchingService
        )
    }
}
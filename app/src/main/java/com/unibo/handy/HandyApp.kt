package com.unibo.handy

import android.app.Application
import com.unibo.handy.data.db.HandyDB
import com.unibo.handy.data.repository.UserRepository
import com.unibo.handy.data.LocationClientSensor
import com.unibo.handy.data.network.WebSocketManager

class HandyApp : Application() {
    val db by lazy { HandyDB.getDatabase(this) }
    private val locationClient by lazy { LocationClientSensor(this) }
    private val webSocketManager by lazy { WebSocketManager() }

    val userRepository by lazy {
        UserRepository(
            db.userDao(),
            db.storedClientDao(),
            webSocketManager = webSocketManager,
            locationClient = locationClient,
            apiService = TODO(),
            matchingService = TODO()
        )
    }
}
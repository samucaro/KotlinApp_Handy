package com.unibo.handy

import android.app.Application
import com.unibo.handy.data.db.HandyDB
import com.unibo.handy.data.repository.UserRepository
import com.unibo.handy.data.LocationClientSensor
import com.unibo.handy.data.network.WebSocketManager
import com.unibo.handy.domain.MatchingService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.unibo.handy.data.network.ServiceAPI


class HandyApp : Application() {
    // DataBase
    val db by lazy { HandyDB.getDatabase(this) }
    // Sensore posizione
    private val locationClient by lazy { LocationClientSensor(this) }
    // WebSocket Manager
    private val webSocketManager by lazy { WebSocketManager() }
    // API Service (Retrofit)
    private val apiService by lazy {
        Retrofit.Builder()
            .baseUrl("http://10.0.2.2:8000/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ServiceAPI::class.java)
    }
    // Matching Service
    private val matchingService by lazy {
        MatchingService(db.storedClientDao())
    }
    val userRepository by lazy {
        UserRepository(
            db.userDao(),
            db.storedClientDao(),
            webSocketManager = webSocketManager,
            locationClient = locationClient,
            apiService = apiService,
            matchingService = matchingService
        )
    }
}
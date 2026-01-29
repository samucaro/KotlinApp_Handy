package com.unibo.handy.data.repository

import android.util.Log
import com.unibo.handy.data.LocationClientSensor
import com.unibo.handy.data.dao.StoredClientDAO
import com.unibo.handy.data.dao.UserDAO
import com.unibo.handy.data.entity.UserEntity
import com.unibo.handy.data.WebSocketManager

class UserRepository(
    private val userDao: UserDAO,
    private val storedClientDao: StoredClientDAO,
    private val webSocketManager: WebSocketManager,
    private val locationClient: LocationClientSensor
) {
    suspend fun startListeningForJobs() {
        // Recuperiamo il nostro ID sessione
        val user = userDao.getLocalUser() ?: return

        // Connettiamo il WebSocket
        webSocketManager.connect(user.userId)

        // Ascoltiamo i messaggi in arrivo
        webSocketManager.incomingMessages.collect { jsonString ->
            handleServerMessage(jsonString)
        }
    }

    private suspend fun handleServerMessage(json: String) {
        // Qui facciamo il parsing manuale o con Gson per capire il tipo di messaggio
        // TIPO 1: "STORE_PROFILE" -> Salviamo nel DB
        // TIPO 2: "COMPUTE_MATCH" -> Eseguiamo PrivacyEngine.computeDistance()

        // Per ora stampiamo solo il log, nel prossimo passo implementeremo la logica esatta
        Log.d("UserRepository", "Devo gestire: $json")
    }
    suspend fun getOrCreateUser(): UserEntity {
        val existingUser = userDao.getLocalUser()

        return if (existingUser != null) {
            existingUser
        } else {
            val newUser = UserEntity()
            userDao.insertUser(newUser)
            newUser
        }
    }

}
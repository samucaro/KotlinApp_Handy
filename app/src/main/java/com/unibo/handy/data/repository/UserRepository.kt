package com.unibo.handy.data.repository

import android.util.Log
import com.google.gson.Gson
import com.unibo.handy.data.LocationClientSensor
import com.unibo.handy.data.db.dao.StoredClientDAO
import com.unibo.handy.data.db.dao.UserDAO
import com.unibo.handy.data.db.entity.UserEntity
import com.unibo.handy.data.network.WebSocketManager
import com.unibo.handy.data.db.entity.StoredClientEntity
import com.unibo.handy.data.network.ServiceAPI
import com.unibo.handy.data.network.dto.HeartBeatDTO
import com.unibo.handy.data.network.dto.RegistrationDTO
import com.unibo.handy.data.repository.strategy.ComputeMatchStrategy
import com.unibo.handy.data.repository.strategy.MessageStrategy
import com.unibo.handy.data.repository.strategy.StoreProfileStrategy
import com.unibo.handy.domain.MatchingService
import com.unibo.handy.domain.PrivacyEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class UserRepository(
    // Dati DB
    private val userDao: UserDAO,
    private val storedClientDao: StoredClientDAO,
    // Dati di rete
    private val webSocketManager: WebSocketManager,
    private val apiService: ServiceAPI,
    // Dati sensori
    private val locationClient: LocationClientSensor,
    // Servizio di matching
    private val matchingService: MatchingService
) {
    val currentUserFlow: Flow<UserEntity?> = userDao.getUserFlow()
    private val gson = Gson()
    private val strategies: Map<String, MessageStrategy> = mapOf(
        "STORE_PROFILE" to StoreProfileStrategy(storedClientDao),
        "COMPUTE_MATCH" to ComputeMatchStrategy(matchingService, webSocketManager, gson)
    )

    suspend fun startListeningForJobs() {
        // Recuperiamo il nostro ID sessione
        val user = userDao.getUserSnapshot() ?: return

        // Connettiamo il WebSocket
        webSocketManager.connect(user.userId)

        // Ascoltiamo i messaggi in arrivo
        webSocketManager.incomingMessages.collect { jsonString ->
            handleServerMessage(jsonString)
        }
    }

    // Metodo di semplice registrazione/aggiornamento nel sistema (profile_update_request)
    suspend fun updateUserProfile(username: String, category: String, isHelper: Boolean) {
        // Verifica esistenza utente nel DB locale
        // Se esiste lo aggiorna, altrimenti crea uno nuovo
        val currentSnap = userDao.getUserSnapshot()
        val userId = currentSnap?.userId ?: java.util.UUID.randomUUID().toString()
        val newUser = UserEntity(
            userId = userId,
            username = username,
            category = category,
            helpModeActive = isHelper
        )
        userDao.insertUser(newUser)

        // Registra/Aggiorna il profilo nel server
        registerOnServer(userId, category, isHelper)
    }

    suspend fun setHelperMode(isActive: Boolean) = withContext(Dispatchers.IO) {
        val user = userDao.getUserSnapshot() ?: return@withContext
        // Aggiorna solo il flag nel DB. La UI reagirà automaticamente grazie al Flow.
        userDao.insertUser(user.copy(helpModeActive = isActive))

        // Sincronizza lo stato con il server
        registerOnServer(user.userId, user.category, isActive)
    }

    private suspend fun registerOnServer(userId: String, category: String, isHelper: Boolean) {
        try {
            val dto = RegistrationDTO(
                clientId = userId,
                category = category,
                isHelper = isHelper
            )

            val response = apiService.registerProfile(dto)
            if (response.isSuccessful) {
                Log.d("UserRepository", "Registration successful")
            } else {
                Log.e("UserRepository", "Registration failed: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e("UserRepository", "Registration error", e)
        }
    }

    /** (Fase 2: Profile-Update-Request Fig. 4b paper)*/
    // Metodo di invio del Heartbeat al server
    suspend fun sendHeartbeat() = withContext(Dispatchers.IO) {
        // 1. CONTROLLO DI SICUREZZA
        val user = userDao.getUserSnapshot()
        if (user == null || !user.helpModeActive) {
            Log.d("UserRepository", "Hertbeat not sent. User not in helper mode.")
            return@withContext
        }

        // 2. RECUPERO GPS
        val location = locationClient.getCurrentLocation()
        if (location == null) {
            Log.w("UserRepository", "Impossible to get GPS position.")
            return@withContext
        }

        // 3. MATEMATICA (Blurring)
        val blurredData = PrivacyEngine.createEncryptedData(
            lat = location.latitude,
            lon = location.longitude
        )

        // 4. INVIO AL SERVER
        try {
            val dto = HeartBeatDTO(
                clientId = user.userId,
                blurredX = blurredData.betaMinusX,
                blurredY = blurredData.betaMinusY,
                encryptedBlur = blurredData.encryptedR //per ora non è cifrato
            )

            val response = apiService.sendHeartbeat(dto)
            if (response.isSuccessful) {
                Log.d("UserRepository", "Heartbeat sent! Position updated.")
            }
        } catch (e: Exception) {
            Log.e("UserRepository", "Heartbeat sent failed", e)
        }
    }

    // Gestore flusso dati dalla rete gestita da un secondo thread dedicato all'I/O
    private suspend fun handleServerMessage(json: String) = withContext(Dispatchers.IO) {
        try {
            // 1. Parsing del json
            val fullMessage = gson.fromJson(json, Map::class.java) as Map<*, *>
            val type = fullMessage["type"] as? String

            // 2. Recupero Strategy corretta
            // (salvataggio->profile-update-request o matching->help-request)
            val strategy = strategies[type]

            strategy?.handle(fullMessage) ?: Log.w("Repo", "Strategy not found: $type")
        } catch (e: Exception) {
            Log.e("UserRepository", "Parsing error from server message", e)
        }
    }
}
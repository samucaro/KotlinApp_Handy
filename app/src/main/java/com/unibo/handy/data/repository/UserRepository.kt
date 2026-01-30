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
    private val locationClient: LocationClientSensor
) {
    val currentUserFlow: Flow<UserEntity?> = userDao.getUserFlow()
    private val gson = Gson()

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

    // Metodo di invio del Heartbeat al server (profile_update_request)
    suspend fun sendHeartbeat() = withContext(Dispatchers.IO) {
        // 1. CONTROLLO DI SICUREZZA
        val user = userDao.getUserSnapshot()
        if (user == null || !user.helpModeActive) {
            Log.d("UserRepository", "Hertbeat not sent. User not in helper mode.")
            return@withContext
        }

        // 2. RECUPERO GPS (Solo se siamo attivi)
        val location = locationClient.getCurrentLocation()
        if (location == null) {
            Log.w("UserRepository", "Impossibile ottenere la posizione GPS.")
            return@withContext
        }

        // 3. MATEMATICA (Blurring)
        val blurredData = PrivacyEngine.createUpdateProfile(
            lat = location.latitude,
            lon = location.longitude
        )

        // 4. INVIO AL SERVER
        try {
            val dto = HeartBeatDTO(
                clientId = user.userId,
                blurredX = blurredData.betaMinusX,
                blurredY = blurredData.betaMinusY,
                encryptedBlur = blurredData.plainNoise //per ora non è cifrato
            )

            val response = apiService.sendHeartbeat(dto)
            if (response.isSuccessful) {
                Log.d("UserRepository", "Heartbeat inviato! Posizione aggiornata.")
            }
        } catch (e: Exception) {
            Log.e("UserRepository", "Fallimento invio heartbeat", e)
        }
    }

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

    // Gestore flusso dati dalla rete
    private suspend fun handleServerMessage(json: String) = withContext(Dispatchers.IO) {
        try {
            // 1. Parsing del json
            val baseMessage = gson.fromJson(json, Map::class.java)
            val type = baseMessage["type"] as? String

            when (type) {
                // CASO A: Il server invia un profilo da custodire
                "STORE_PROFILE" -> {
                    val payload = baseMessage["payload"] as Map<*, *>
                    handleStoreCommand(payload)
                }

                /* CASO B: Un utente ha chiesto aiuto e quindi fare il matching
                "COMPUTE_MATCH" -> {
                    val requesterData = baseMessage["requester_data"] as Map<*, *>
                    handleComputeMatch(requesterData)
                }*/
            }
        } catch (e: Exception) {
            Log.e("UserRepository", "Parsing error from server message", e)
        }
    }

    private suspend fun handleStoreCommand(payload: Map<*, *>) {
        val entity = StoredClientEntity(
            clientId = payload["client_Id"] as String,
            reblurredX = (payload["reblurred_x"] as Double).toLong(),
            reblurredY = (payload["reblurred_y"] as Double).toLong(),
            category = (payload["category"] as Double).toString()
        )
        storedClientDao.saveProfile(entity)
        Log.d("UserRepository", "${entity.clientId.take(5)}'s profile saved successfully.")
    }

    /*
    private suspend fun handleComputeMatch(requesterData: Map<*, *>) {
        val targetUuid = requesterData["target_uuid"] as? String ?: return

        // 1. Recuperiamo il profilo di "B" dal nostro DB locale
        val storedProfile = storedClientDao.getProfile(targetUuid) ?: return

        // 2. Estraiamo i dati del richiedente (β+) dalla Tupla ricevuta
        val betaPlusX = (requesterData["beta_plus_x"] as Double).toLong()
        val betaPlusY = (requesterData["beta_plus_y"] as Double).toLong()
        val tolerance = (requesterData["tolerance"] as Double).toLong()

        // 3. CALCOLO MATEMATICO (PrivacyEngine)
        // Utilizziamo le coordinate memorizzate (β-) e quelle ricevute (β+)
        val isMatch = PrivacyEngine.checkProximity(
            betaPlusX = betaPlusX,
            betaPlusY = betaPlusY,
            betaMinusX = storedProfile.reblurredX,
            betaMinusY = storedProfile.reblurredY,
            tolerance = tolerance
        )

        if (isMatch) {
            Log.d("UserRepository", "!!! MATCH TROVATO per $targetUuid !!!")
            // Notifichiamo il server tramite WebSocket
            val matchFoundMsg = mapOf(
                "type" to "MATCH_FOUND",
                "target_uuid" to targetUuid
            )
            webSocketManager.sendMessage(gson.toJson(matchFoundMsg))
        }
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
    }*/
}
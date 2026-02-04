package com.unibo.handy.data.repository

import android.util.Log
import com.google.gson.Gson
import com.unibo.handy.data.LocationClientSensor
import com.unibo.handy.data.db.dao.StoredClientDAO
import com.unibo.handy.data.db.dao.UserDAO
import com.unibo.handy.data.db.entity.UserEntity
import com.unibo.handy.data.network.WebSocketManager
import com.unibo.handy.data.network.ServiceAPI
import com.unibo.handy.data.network.dto.HeartBeatDTO
import com.unibo.handy.data.network.dto.HelpRequestDTO
import com.unibo.handy.data.network.dto.RegistrationDTO
import com.unibo.handy.data.repository.strategy.ComputeMatchStrategy
import com.unibo.handy.data.repository.strategy.MessageStrategy
import com.unibo.handy.data.repository.strategy.StoreProfileStrategy
import com.unibo.handy.domain.MatchingService
import com.unibo.handy.domain.PrivacyEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private val _matchEvents = MutableSharedFlow<String>()
    val matchEvents = _matchEvents.asSharedFlow()
    private val gson = Gson()
    private val strategies: Map<String, MessageStrategy> = mapOf(
        "STORE_PROFILE" to StoreProfileStrategy(storedClientDao),
        "UPDATE_PROFILE" to StoreProfileStrategy(storedClientDao),
        "UPDATE_RATINGDATA" to StoreProfileStrategy(storedClientDao),
        "COMPUTE_MATCH" to ComputeMatchStrategy(matchingService, webSocketManager, gson) { matchInfo ->
            Log.d("UserRepository", "Callback Match attivata! Notifico la UI.")
            _matchEvents.tryEmit(matchInfo)
        }
    )

    // Metodo di semplice registrazione/aggiornamento nel sistema (profile_update_request)
    suspend fun updateUserProfile(username: String, email: String, psw: String, category: String) {
        // Verifica esistenza utente nel DB locale
        // Se esiste lo aggiorna, altrimenti crea uno nuovo
        val currentSnap = userDao.getUserSnapshot()
        val userId = currentSnap?.userId ?: java.util.UUID.randomUUID().toString()
        val isHelper = currentSnap?.helpModeActive ?: false

        val newUser = UserEntity(userId, username, email, psw, category)
        userDao.insertUser(newUser)

        // Registra/Aggiorna il profilo nel server al quale servono solo 3 dei 7 campi
        registerOnServer(userId, category, isHelper)
    }

    // Metodo per aggiornare lo stato di modalità di aiuto
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

            // Utilizzando Retrofit qui c'è il cambio di thread da Dispatchers.IO
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

    /**
      *(Fase 2: Profile-Update-Request Fig. 4b paper)
      *Solo per Helper client
     **/
    // Attiva la connessione stateful con il server tramite WebSocket
    suspend fun startListeningForJobs() {
        // Recuperiamo il nostro ID sessione
        val user = userDao.getUserSnapshot() ?: return

        Log.d("HandyWS", "Tentativo connessione per ID: ${user.userId}")
        // Connette il WebSocket
        webSocketManager.connect(user.userId)

        // Ascoltiamo i messaggi in arrivo
        webSocketManager.incomingMessages.collect { jsonString ->
            handleServerMessage(jsonString)
        }
    }

    // Metodo di invio del Heartbeat al server valido solo per le coordinate GPS
    suspend fun sendHeartbeat() = withContext(Dispatchers.IO) {
        Log.e("HandyDEBUG", "--- INIZIO HEARTBEAT ---")
        // 1. CONTROLLO DI SICUREZZA
        val user = userDao.getUserSnapshot()

        if (user == null || !user.helpModeActive) {
            Log.e("HandyDEBUG", "STOP: Utente null nel DB")
            Log.d("UserRepository", "Hertbeat not sent. User not in helper mode.")
            return@withContext
        }
        Log.e("HandyDEBUG", "Utente OK. Richiedo posizione GPS...")

        // 2. RECUPERO GPS
        val location = locationClient.getCurrentLocation()
        if (location == null) {
            Log.e("HandyDEBUG", "STOP: LocationClient ha restituito NULL! (Controlla permessi o Emulatore)")
            Log.w("UserRepository", "Impossible to get GPS position.")
            return@withContext
        }
        Log.e("HandyDEBUG", "Posizione trovata: ${location.latitude}, ${location.longitude}")

        // 3. MATEMATICA (Blurring)
        val blurredData = PrivacyEngine.createEncryptedData(
            lat = location.latitude,
            lon = location.longitude
        )
        Log.e("HandyDEBUG", "Blurring completato. Invio al server...")

        // 4. INVIO AL SERVER
        // Rispetto al paper vengono inviati solo gli aggiornamenti periodici della posizione
        try {
            val dto = HeartBeatDTO(
                clientId = user.userId,
                blurredData.betaMinusX,
                blurredData.betaMinusY,
                encryptedBlur = blurredData.encryptedR //per ora non è cifrato
            )

            val response = apiService.sendHeartbeat(dto)
            if (response.isSuccessful) {
                Log.e("HandyDEBUG", "SUCCESS: Heartbeat inviato e ricevuto dal server (200 OK)!")
                Log.d("UserRepository", "Heartbeat sent! Position updated.")
            } else {
                Log.e("HandyDEBUG", "ERRORE SERVER: Codice ${response.code()} - ${response.errorBody()?.string()}")
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

    suspend fun sendHelpRequest(category: String,  tolerance: Double) {
        val user = userDao.getUserSnapshot() ?: throw IllegalStateException("Utente non loggato!")

        val location = locationClient.getCurrentLocation()
        if (location == null) {
            Log.w("UserRepository", "Impossible to get GPS position.")
            return
        }

        // 3. Offuscamento (Uso PrivacyEngine - Fase REQUEST)
        val blurredData = PrivacyEngine.createHelpRequest(
            lat = location.latitude,
            lon = location.longitude,
            tol = tolerance
        )

        // 4. Creazione DTO
        val dto = HelpRequestDTO(
            clientId = user.userId,
            category = category,
            blurredX = blurredData.betaPlusX,
            blurredY = blurredData.betaPlusY,
            encryptedR = blurredData.encryptedR, //Da cifrare con Paillier
            encryptedTol = blurredData.encryptedTol //Da cifrare con Paillier
        )

        // 5. Invio al Server
        // Il server riceverà questo DTO e lo inoltrerà ai Service Clients
        // che custodiscono gli idraulici per fare il matching.
        apiService.sendHelpRequest(dto)
    }
}
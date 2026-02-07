package com.unibo.handy.data.repository

import android.util.Log
import com.google.gson.Gson
import com.unibo.handy.data.LocationClientSensor
import com.unibo.handy.data.db.dao.ChatDAO
import com.unibo.handy.data.db.dao.MatchDAO
import com.unibo.handy.data.db.dao.StoredClientDAO
import com.unibo.handy.data.db.dao.UserDAO
import com.unibo.handy.data.db.entity.MatchEntity
import com.unibo.handy.data.db.entity.UserEntity
import com.unibo.handy.data.network.ServiceAPI
import com.unibo.handy.data.network.WebSocketManager
import com.unibo.handy.data.network.dto.HeartBeatDTO
import com.unibo.handy.data.network.dto.HelpRequestDTO
import com.unibo.handy.data.network.dto.RegistrationDTO
import com.unibo.handy.data.repository.strategy.ComputeMatchStrategy
import com.unibo.handy.data.repository.strategy.MessageStrategy
import com.unibo.handy.data.repository.strategy.StoreProfileStrategy
import com.unibo.handy.data.repository.strategy.UpdatePositionStrategy
import com.unibo.handy.data.repository.strategy.UpdateRatingDataStrategy
import com.unibo.handy.domain.MatchingService
import com.unibo.handy.domain.PrivacyEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UserRepository(
    // Dati DB
    private val userDao: UserDAO,
    storedClientDao: StoredClientDAO,
    private val matchDao: MatchDAO,
    private val chatDao: ChatDAO,
    // Dati di rete
    private val webSocketManager: WebSocketManager,
    private val apiService: ServiceAPI,
    // Dati sensori
    private val locationClient: LocationClientSensor,
    // Servizio di matching
    matchingService: MatchingService
) {
    private val repositoryScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val currentUserFlow: Flow<UserEntity?> = userDao.getUserFlow()
    val matchesFlow: Flow<List<MatchEntity>> = matchDao.getAllMatches()

    private val _matchEvents = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val matchEvents = _matchEvents.asSharedFlow()
    private val gson = Gson()
    private val strategies: Map<String, MessageStrategy> = mapOf(
        "STORE_PROFILE" to StoreProfileStrategy(storedClientDao),
        "UPDATE_PROFILE" to StoreProfileStrategy(storedClientDao),
        "UPDATE_POSITION" to UpdatePositionStrategy(storedClientDao),
        //"UPDATE_RATINGDATA" to UpdateRatingDataStrategy(storedClientDao),
        "COMPUTE_MATCH" to ComputeMatchStrategy(matchingService, webSocketManager, gson) { matchInfo ->
            Log.d("UserRepository", "Callback Match attivata! Notifico la UI.")
            Log.i("HandyFlow", "MATCH CALCOLATO POSITIVO! Info: $matchInfo")
            saveMatchToDb(matchInfo)
            //_matchEvents.tryEmit(matchInfo)
        }
    )

    // Attiva la connessione stateful con il server tramite WebSocket
    suspend fun ensureWebSocketConnection() {
        val user = userDao.getUserSnapshot() ?: return

        if (!webSocketManager.isConnected()) {
            Log.d("HandyWS", "Connessione automatica per: ${user.userId}")
            webSocketManager.connect(user.userId)
            // Usiamo un job separato o un collect che non blocchi il chiamante
            // se viene chiamato più volte
            startListeningForMessages()
        }
    }
    private suspend fun startListeningForMessages() {
        repositoryScope.launch {
            webSocketManager.incomingMessages.collect { jsonString ->
                handleServerMessage(jsonString)
            }
        }
    }

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
            Log.e("HandyFlow", "Impossibile inviare richiesta: GPS nullo.")
            return
        }

        Log.d("HandyFlow", "Posizione Reale Richiedente: ${location.latitude}, ${location.longitude}")

        // 3. Offuscamento (Uso PrivacyEngine - Fase REQUEST)
        val blurredData = PrivacyEngine.createHelpRequest(
            lat = location.latitude,
            lon = location.longitude,
            tol = tolerance
        )

        Log.d("HandyFlow", "Dati offuscati generati (Beta+): X=${blurredData.betaPlusX}, Y=${blurredData.betaPlusY}")

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
        try {
            val response = apiService.sendHelpRequest(dto)
            if (response.isSuccessful) {
                Log.i("HandyFlow", "Richiesta Aiuto inviata con successo al Server!")
            } else {
                Log.e("HandyFlow", "Errore invio richiesta: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e("HandyFlow", "Eccezione di rete invio richiesta", e)
        }
    }

    private fun saveMatchToDb(requesterId: String) {
        repositoryScope.launch {
            val myProfile = userDao.getUserSnapshot()
            if (myProfile == null) {
                Log.e("HandyRepo", "Impossibile salvare match: Profilo utente locale non trovato.")
                return@launch
            }

            val newMatch = MatchEntity(
                requesterId = requesterId,
                helperId = myProfile.userId,
                username = "Richiedente ${requesterId.take(4)}",
                category = myProfile.category,
                phoneNumber = "ND" // da aggiornare
            )
            matchDao.insertMatch(newMatch)

            _matchEvents.tryEmit(requesterId)
        }
    }
}
package com.unibo.handy.data.repository

import android.util.Log
import com.unibo.handy.data.db.dao.UserDAO
import com.unibo.handy.data.db.entity.UserEntity
import com.unibo.handy.data.network.ServiceAPI
import com.unibo.handy.data.network.dto.HelpRequestDTO
import com.unibo.handy.data.network.dto.RegistrationDTO
import com.unibo.handy.domain.PrivacyEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Repository responsabile della gestione dell'identità dell'utente corrente.
 * Gestisce:
 * 1. Dati anagrafici (Username, Categoria, ecc.)
 * 2. Stato "Helper" (Attivo/Non Attivo)
 * 3. Sincronizzazione del profilo con il server via HTTP (Registrazione).
 */
class UserRepository(
    // Dati DB
    private val userDao: UserDAO,
    // Dati di rete
    private val apiService: ServiceAPI,
    // Gestore di posizione
    private val locationRepo: LocationRepository
) {
    // Il Flow permette la reattività istantane a modifiche nel DB locale
    val currentUserFlow: Flow<UserEntity?> = userDao.getUserFlow()

    // Metodo di semplice registrazione/aggiornamento nel sistema (profile_update_request, interazione con VM)
    suspend fun updateUserProfile(username: String, email: String, psw: String) {
        Log.i("UserRepo", "Updating profile: $username")

        // Verifica esistenza utente nel DB locale
        // Se esiste lo aggiorna, altrimenti crea uno nuovo
        val currentSnap = userDao.getUserSnapshot()
        val userId = currentSnap?.userId ?: UUID.randomUUID().toString()
        val isHelper = currentSnap?.helpModeActive ?: false

        val newUser = UserEntity(
            userId = userId,
            username = username,
            email = email,
            passwordHash = psw, //hashare psw
            category = "Generico",
            helpModeActive = isHelper
        )

        try {
            // Registra/Aggiorna il profilo nel server al quale servono solo 3 dei 7 campi
            registerOnServer(userId, "Generico", isHelper)
        } catch (e: Exception) {
            Log.e("UserRepo", "Server unreachable: cannot register user.")
            throw Exception("Server non disponibile: impossibile registrarsi.")
        }

        userDao.insertUser(newUser)
        Log.d("UserRepo", "User saved: $userId")
    }

    // Metodo per aggiornare lo stato di modalità di aiuto
    suspend fun setHelperMode(isActive: Boolean, category: String) = withContext(Dispatchers.IO) {
        val user = userDao.getUserSnapshot()

        if (user == null) {
            Log.e("UserRepo", "Impossible to set helper mode: User not found")
            return@withContext
        }

        Log.i("UserRepo", "Setting helper mode: $isActive")

        // Aggiorna solo il parametro helpModeActive nel DB. La UI reagirà automaticamente grazie al Flow.
        userDao.insertUser(user.copy(helpModeActive = isActive, category = category))

        // Sincronizza lo stato con il server
        registerOnServer(user.userId, category, isActive)
    }

    // Canale di comunicazione con il server tramite Retrofit REST
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
                throw Exception("Errore Server: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e("UserRepository", "Registration error", e)
        }
    }

    /**
     * FASE 3: HELP-REQUEST (Fig. 5b paper)
     * Metodo di invio richiesta di aiuto usa il canale Retrofit REST
     */
    suspend fun sendHelpRequest(userId: String, category: String,  tolerance: Double) {
        // 1. RECUPERO POSIZIONE
        val location = locationRepo.getCurrentLocation()
        if (location == null) {
            Log.e("MatchingRepo", "HelpRequest skipped: GPS null.")
            return
        }

        Log.i("MatchingRepo", "HelpRequest sent for Category: $category, Tol: $tolerance")

        // 2. BLURRING
        val blurredData = PrivacyEngine.createHelpRequest(
            lat = location.latitude,
            lon = location.longitude,
            tol = tolerance
        )

        try {
            // 3. CREAZIONE DTO
            val dto = HelpRequestDTO(
                clientId = userId,
                category = category,
                blurredX = blurredData.betaPlusX,
                blurredY = blurredData.betaPlusY,
                encryptedR = blurredData.encryptedR, //Da cifrare con Paillier
                encryptedTol = blurredData.encryptedTol //Da cifrare con Paillier
            )

            // 4. INVIO AL SERVER
            // Il server riceverà questo DTO e lo inoltrerà ai Service Clients
            // che custodiscono gli helper per fare il matching.
            val response = apiService.sendHelpRequest(dto)

            if (response.isSuccessful) {
                Log.i("MatchingRepo", "HelpRequest success (200 OK). Request recieve by server")
            } else {
                Log.e("MatchingRepo", "HelpRequest server error: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e("MatchingRepo", "HelpRequest network error", e)
        }
    }

    // Funzione helper per il logout
    suspend fun logout() = withContext(Dispatchers.IO) {
        Log.w("UserRepo", "Logout")
        userDao.deleteUser(userDao.getUserSnapshot()?.userId ?: "")
    }
}
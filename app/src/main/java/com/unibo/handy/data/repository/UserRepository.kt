package com.unibo.handy.data.repository

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.unibo.handy.data.db.dao.UserDAO
import com.unibo.handy.data.db.entity.UserEntity
import com.unibo.handy.data.network.ServiceAPI
import com.unibo.handy.data.network.dto.FcmTokenDTO
import com.unibo.handy.data.network.dto.HelpRequestDTO
import com.unibo.handy.data.network.dto.RegistrationDTO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository responsabile della gestione dell'identità dell'utente corrente.
 * Gestisce:
 * 1. Dati anagrafici (Username, Categoria, ecc.)
 * 2. Stato "Helper" (Attivo/Non Attivo)
 * 3. Sincronizzazione del profilo con il server via HTTP (Registrazione).
 */
@Singleton
class UserRepository @Inject constructor(
    private val userDao: UserDAO,
    private val apiService: ServiceAPI,
    private val secureKeyRepository: SecureKeyRepository
) {
    // Il Flow permette la reattività istantane a modifiche nel DB locale
    val currentUserFlow: Flow<UserEntity?> = userDao.getUserFlow()
    // Variabile in memoria per tenere traccia dell'ultimo token ricevuto da Firebase
    private var latestFcmToken: String? = null

    // Metodo di registrazione/aggiornamento nel sistema (profile_update_request, interazione con VM)
    suspend fun updateUserProfile(username: String, email: String, psw: String) {
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
            registerOnServer(userId, "Generico", isHelper)
        } catch (_: Exception) {
            throw Exception("Server non disponibile: impossibile registrarsi.")
        }

        userDao.insertUser(newUser)
    }

    // Metodo per aggiornare lo stato di modalità di aiuto
    suspend fun setHelperMode(isActive: Boolean, category: String) = withContext(Dispatchers.IO) {
        val user = userDao.getUserSnapshot()

        if (user == null) {
            Log.e("UserRepo", "Impossible to set helper mode: User not found")
            return@withContext
        }

        // RECUPERO DEL TOKEN FCM
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            latestFcmToken = token
        } catch (e: Exception) {
            Log.e("UserRepo", "Impossibile recuperare il token FCM", e)
        }

        userDao.insertUser(user.copy(helpModeActive = isActive, category = category))

        registerOnServer(user.userId, category, isActive)
    }

    /**
     * Viene chiamato dal HandyFcmService quando Firebase genera un nuovo Token.
     * Salva il token e lo invia subito al server se l'utente è loggato.
     */
    suspend fun updateFcmToken(token: String) = withContext(Dispatchers.IO) {
        latestFcmToken = token

        val user = userDao.getUserSnapshot()
        if (user != null) {
            try {
                val response = apiService.updateFcmToken(FcmTokenDTO(user.userId, token))

                if (!response.isSuccessful) {
                    Log.e("UserRepo", "Error updating FCM Token: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("UserRepo", "Network error sync FCM Token", e)
            }
        }
    }

    // Canale di comunicazione con il server tramite Retrofit REST
    private suspend fun registerOnServer(userId: String, category: String, isHelper: Boolean) {
        try {
            val modulus = secureKeyRepository.getPublicModulus()
            val dto = RegistrationDTO(
                clientId = userId,
                category = category,
                isHelper = isHelper,
                fcmToken = latestFcmToken,
                publicModulus = modulus?.toString()
            )

            val response = apiService.registerProfile(dto)

            if (!response.isSuccessful) {
                Log.e("UserRepository", "Registration failed: ${response.code()}")
                throw Exception("Error Server: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e("UserRepository", "Registration error", e)
            throw Exception("Server unreachable. Check your connection and try again.")
        }
    }

    suspend fun postHelpRequestToNetwork(
        userId: String,
        category: String,
        blurredX: Long,
        blurredY: Long,
        encryptedR: String,
        encryptedTol: String,
        publicModulus: String
    ) {
        val dto = HelpRequestDTO(
            clientId = userId,
            category = category,
            blurredX = blurredX,
            blurredY = blurredY,
            encryptedR = encryptedR,
            encryptedTol = encryptedTol,
            publicModulus = publicModulus
        )

        val response = apiService.sendHelpRequest(dto)

        if (!response.isSuccessful) throw Exception("Server Error: ${response.code()}")
    }

    // Helper per l'Heartbeat UseCase
    suspend fun getCurrentUserSnapshot() = userDao.getUserSnapshot()

    // Funzione helper per il logout
    suspend fun logout() = withContext(Dispatchers.IO) {
        userDao.deleteUser(userDao.getUserSnapshot()?.userId ?: "")
    }
}
package com.unibo.handy.data.repository

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.unibo.handy.data.db.HandyDB
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
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository responsabile della gestione dell'identità dell'utente corrente (SSOT - Single Source of Truth).
 * Sincronizza lo stato locale (Room) con il backend Python (Retrofit).
 */
@Singleton
class UserRepository @Inject constructor(
    private val userDao: UserDAO,
    private val db: HandyDB,
    private val apiService: ServiceAPI,
    private val secureKeyRepository: SecureKeyRepository
) {
    // Flow reattivo: la UI si aggiorna automaticamente se il DB cambia
    val currentUserFlow: Flow<UserEntity?> = userDao.getUserFlow()
    private var latestFcmToken: String? = null

    /**
     * Registra o aggiorna il profilo utente.
     * Implementa il paradigma Offline-First con salvataggio asincrono.
     */
    suspend fun updateUserProfile(username: String, email: String, psw: String) {
        val currentSnap = userDao.getUserSnapshot()
        val userId = currentSnap?.userId ?: UUID.randomUUID().toString()
        val isHelper = currentSnap?.helpModeActive ?: false

        // 1. Generazione dell'Hash Crittografico della Password
        val hashedPassword = hashPassword(psw)

        // 2. Sincronizzazione di Rete Sincrona (Fail-Fast)
        // Se il server è irraggiungibile, viene lanciata un'eccezione,
        // l'esecuzione si interrompe e non salva nulla in locale.
        try {
            registerOnServer(userId, "Generico", isHelper)
        } catch (_: Exception) {
            Log.e("UserRepo", "Server offline. Registrazione annullata.")
            throw Exception("Server non disponibile: impossibile registrarsi in questo momento.")
        }

        // 3. Persistenza Locale
        val newUser = UserEntity(
            userId = userId,
            username = username,
            email = email,
            passwordHash = hashedPassword,
            category = "Generico",
            helpModeActive = isHelper
        )
        userDao.insertUser(newUser)
    }

    /**
     * Funzione di utilità per l'hashing crittografico (SHA-256).
     * Converte la stringa in un array di byte, ne calcola il digest e lo trasforma in esadecimale.
     */
    private fun hashPassword(password: String): String {
        val bytes = password.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    suspend fun setHelperMode(isActive: Boolean, category: String) = withContext(Dispatchers.IO) {
        val user = userDao.getUserSnapshot() ?: return@withContext

        // RECUPERO DEL TOKEN FCM
        try {
            latestFcmToken = FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            Log.e("UserRepo", "Impossibile recuperare il token FCM", e)
        }

        userDao.insertUser(user.copy(helpModeActive = isActive, category = category))
        registerOnServer(user.userId, category, isActive)
    }

    suspend fun updateFcmToken(token: String) = withContext(Dispatchers.IO) {
        latestFcmToken = token
        val user = userDao.getUserSnapshot() ?: return@withContext

        try {
            apiService.updateFcmToken(FcmTokenDTO(user.userId, token))
        } catch (e: Exception) {
            Log.e("UserRepo", "Network error sync FCM Token", e)
        }
    }

    private suspend fun registerOnServer(userId: String, category: String, isHelper: Boolean) {
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
            throw Exception("Error Server: ${response.code()}")
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

    suspend fun getCurrentUserSnapshot() = userDao.getUserSnapshot()

    /**
     * Termina la sessione utente garantendo la Privacy by Design.
     * Invece di cancellare solo l'utente, esegue un "Wipe" completo del database locale,
     * distruggendo lo storico chat, i match pendenti e i profili offuscati custoditi.
     */
    suspend fun logout() = withContext(Dispatchers.IO) {
        // clearAllTables() svuota TUTTE le tabelle definite in HandyDB contemporaneamente.
        // Essendo reattivo, Room avviserà tutti i Flow che le tabelle sono vuote.
        // Il currentUserFlow emetterà 'null' e l'AuthViewModel chiuderà l'app istantaneamente!
        db.clearAllTables()
    }
}
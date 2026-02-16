package com.unibo.handy.data.repository

import android.util.Log
import com.unibo.handy.data.db.dao.UserDAO
import com.unibo.handy.data.db.entity.UserEntity
import com.unibo.handy.data.network.ServiceAPI
import com.unibo.handy.data.network.dto.RegistrationDTO
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
) {
    // Il Flow permette la reattività istantane a modifiche nel DB locale
    val currentUserFlow: Flow<UserEntity?> = userDao.getUserFlow()

    // Metodo di semplice registrazione/aggiornamento nel sistema (profile_update_request, interazione con VM)
    suspend fun updateUserProfile(username: String, email: String, psw: String, category: String) {
        Log.i("UserRepo", "Updating profile: $username ($category)")

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
            category = category,
            helpModeActive = isHelper
        )
        userDao.insertUser(newUser)
        Log.d("UserRepo", "User saved: $userId")

        // Registra/Aggiorna il profilo nel server al quale servono solo 3 dei 7 campi
        registerOnServer(userId, category, isHelper)
    }

    // Metodo per aggiornare lo stato di modalità di aiuto
    suspend fun setHelperMode(isActive: Boolean) = withContext(Dispatchers.IO) {
        val user = userDao.getUserSnapshot()

        if (user == null) {
            Log.e("UserRepo", "Impossible to set helper mode: User not found")
            return@withContext
        }

        Log.i("UserRepo", "Setting helper mode: $isActive")

        // Aggiorna solo il flag nel DB. La UI reagirà automaticamente grazie al Flow.
        userDao.insertUser(user.copy(helpModeActive = isActive))

        // Sincronizza lo stato con il server
        registerOnServer(user.userId, user.category, isActive)
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
            }
        } catch (e: Exception) {
            Log.e("UserRepository", "Registration error", e)
        }
    }

    // Funzione helper per il logout
    suspend fun logout() = withContext(Dispatchers.IO) {
        Log.w("UserRepo", "Logout")
        userDao.deleteUser(userDao.getUserSnapshot()?.userId ?: "")
    }
}
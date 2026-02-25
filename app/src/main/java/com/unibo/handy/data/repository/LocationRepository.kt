package com.unibo.handy.data.repository

import android.location.Location
import android.util.Log
import com.unibo.handy.data.LocationClientSensor
import com.unibo.handy.data.db.dao.UserDAO
import com.unibo.handy.data.network.ServiceAPI
import com.unibo.handy.data.network.dto.HeartBeatDTO
import com.unibo.handy.domain.PaillierEncryption
import com.unibo.handy.domain.PrivacyEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigInteger
import javax.inject.Inject

/**
 * Gestisce l'interazione con il sensore GPS e l'invio periodico della posizione (Heartbeat).
 * Implementa la Fase 2 del protocollo: Profile-Update-Request.
 */
class LocationRepository @Inject constructor(
    private val locationClient: LocationClientSensor,
    private val apiService: ServiceAPI,
    private val userDao: UserDAO,
    private val secureKeyRepository: SecureKeyRepository
) {
    //Recupera la posizione attuale (Wrap del sensore)
    suspend fun getCurrentLocation(): Location? {
        return locationClient.getCurrentLocation()
    }

    /**
     *(Fase 2: Profile-Update-Request Fig. 4b paper)
     *Solo per Helper client, usa il canale Retrofit REST
     **/
    // Metodo di invio del Heartbeat al server valido solo per le coordinate GPS (canale Retrofit REST)
    suspend fun sendHeartbeat() = withContext(Dispatchers.IO) {
        Log.d("LocationRepo", "--- INIZIO HEARTBEAT ---")

        // 1. RECUPERO DATI UTENTE
        val user = userDao.getUserSnapshot()
        if (user == null || !user.helpModeActive || user.category == "Generico") {
            Log.d("LocationRepo", "Hertbeat not sent. User null or not in helper mode.")
            return@withContext
        }

        // 2. RECUPERO GPS
        val location = locationClient.getCurrentLocation()
        if (location == null) {
            Log.w("LocationRepo", "Heartbeat skipped: GPS null.")
            return@withContext
        }

        // 3. Recupero Modulo Pubblico per la cifratura
        val modulus = secureKeyRepository.getPublicModulus()
        if (modulus == null) {
            Log.e("LocationRepo", "Error: Missing public module. Encryption not possible.")
            return@withContext
        }

        // 3. MATEMATICA (Blurring)
        val blurredData = PrivacyEngine.createEncryptedData(
            lat = location.latitude,
            lon = location.longitude
        )

        // 4. Cifratura omomorfica del blur r
        val rawNoiseBigInt = BigInteger.valueOf(blurredData.encryptedR)
        val encryptedBlur = PaillierEncryption.encrypt(rawNoiseBigInt, modulus)

        Log.d("LocationRepo", "Blurred position generated: X=${blurredData.betaMinusX}, Y=${blurredData.betaMinusY}")
        Log.w("CryptoProof", """
                --- PROVA CRITTOGRAFICA HEARTBEAT ---
                1. Rumore generato (r in chiaro): $rawNoiseBigInt
                2. Coordinata X Offuscata (X + r): ${blurredData.betaMinusX}
                3. Rumore r Cifrato con Paillier E(r): ${encryptedBlur.toString().take(30)}... [TRONCATO, lunghezza reale: ${encryptedBlur.toString().length} cifre!]
                """.trimIndent())
        // 4. CHIAMATA DI RETE (Stateless)
        // Rispetto al paper vengono inviati solo gli aggiornamenti periodici della posizione
        try {
            val dto = HeartBeatDTO(
                clientId = user.userId,
                blurredX = blurredData.betaMinusX,
                blurredY = blurredData.betaMinusY,
                encryptedBlur = encryptedBlur.toString()
            )

            val response = apiService.sendHeartbeat(dto)

            if (response.isSuccessful) {
                Log.i("LocationRepo", "Heartbeat success (200 OK). Position receive by server.")
            } else {
                Log.e("LocationRepo", "Heartbeat server error: ${response.code()} - ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e("LocationRepo", "Heartbeat network exception", e)
        }
    }
}
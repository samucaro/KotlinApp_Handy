package com.unibo.handy.data.repository

import android.util.Log
import com.google.gson.Gson
import com.unibo.handy.data.db.dao.MatchDAO
import com.unibo.handy.data.db.dao.StoredClientDAO
import com.unibo.handy.data.db.entity.MatchEntity
import com.unibo.handy.data.db.entity.MatchStatus
import com.unibo.handy.data.db.entity.ProfileData
import com.unibo.handy.data.db.entity.StoredClientEntity
import com.unibo.handy.data.network.WebSocketManager
import com.unibo.handy.data.network.dto.StoreProfileDTO
import com.unibo.handy.data.network.dto.TuplaDTO
import com.unibo.handy.domain.MatchingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository centrale per il protocollo SamaritanCloud.
 * Gestisce:
 * 1. Ruolo Richiedente: Invio Help-Request (Beta+).
 * 2. Ruolo Service Client: Custodia profili offuscati e Calcolo Match
 */
@Singleton
class MatchingRepository @Inject constructor(
    private val webSocketManager: WebSocketManager,
    private val matchDao: MatchDAO,
    private val storedClientDao: StoredClientDAO,
    private val matchingService: MatchingService,
    private val secureKeyRepository: SecureKeyRepository
) {
    private val gson: Gson = Gson()
    // Flow per notificare la UI di nuovi match
    private val _matchEvents = MutableSharedFlow<String>(replay = 0)
    val matchEvents = _matchEvents.asSharedFlow()

    private val _requesterMatchEvents = MutableSharedFlow<String>(replay = 0)
    val requesterMatchEvents = _requesterMatchEvents.asSharedFlow()

    // Gestione dei messaggi WebSocket specifici per il matching.
    suspend fun handleComputeMatch(payload: TuplaDTO) = withContext(Dispatchers.IO) {
        try {
            Log.d("MatchingRepo", "ComputeMatch received for: ${payload.t2TargetId}")

            // Recupero chiavi crittografiche ---
            val privateKey = secureKeyRepository.getPrivateKey()
            val modulus = secureKeyRepository.getPublicModulus()

            // Controllo di sicurezza: se non abbiamo le chiavi, l'utente non si è registrato correttamente
            if (privateKey == null || modulus == null) {
                Log.e("MatchingRepo", "CRITICAL ERROR: Keys not found in hardware keystore. Cannot perform match.")
                return@withContext
            }

            // Delega al Domain Service la matematica pura
            val isCompatible = matchingService.verifyMatch(payload, privateKey, modulus)

            if (isCompatible) {
                Log.i("MatchingRepo", "MATCH FOUND! Saving to db...")

                // Salva nel DB locale
                saveMatchToDb(payload.t1RequesterId, payload.t2TargetId)

                //Notifica il server
                notifyServerOfMatch(payload.t1RequesterId, payload.t2TargetId)
            } else {
                Log.d("MatchingRepo", "No match found")
            }
        } catch (e: Exception) {
            Log.e("MatchingRepo", "Error in handleComputeMatch", e)
        }
    }

    private suspend fun saveMatchToDb(requesterId: String, helperId: String) {
        val newMatch = MatchEntity(
            requesterId = requesterId,
            helperId = helperId,
            username = requesterId.take(4),
            category = "General",
            phoneNumber = "ND",
            isMeHelper = true,
            status = MatchStatus.PENDING
        )
        matchDao.insertMatch(newMatch)
        _matchEvents.emit(requesterId)
    }

    private fun notifyServerOfMatch(requesterId: String, targetId: String) {
        val payloadMap = mapOf(
            "type" to "MATCH_FOUND",
            "payload" to mapOf(
                "requester_id" to requesterId,
                "target_id" to targetId
            )
        )

        val jsonString = gson.toJson(payloadMap)

        val sent = webSocketManager.sendMessage(jsonString)
        if (!sent) {
            Log.w("MatchingRepo", "Match notification not sent")
        }
    }

    // Gestisce il salvataggio dei profili offuscati da custodire per il matching
    suspend fun handleStoreProfile(payload: StoreProfileDTO) = withContext(Dispatchers.IO) {
        try {
            val profileBlob = ProfileData(
                reblurredX = payload.reblurredX,
                reblurredY = payload.reblurredY,
                username = payload.username,
                category = payload.category,
                rating = payload.rating
            )

            // Creazione dell'entità per il DB locale
            val entity = StoredClientEntity(
                clientId = payload.targetId,
                profile = profileBlob
            )

            // Salvataggio nel DB
            storedClientDao.saveProfile(entity)

            Log.d("MatchingRepo", "Profile saved: ${payload.targetId} ")

        } catch (e: Exception) {
            Log.e("MatchingRepo", "Error in handleStoreProfile", e)
        }
    }

    suspend fun handleMatchFoundNotification(helperId: String) {
        Log.i("MatchingRepo", "Il server ci avvisa che l'Helper $helperId ha calcolato un match positivo!")

        // Salva la chat nel DB. Qui è il Richiedente!
        val newMatch = MatchEntity(
            requesterId = helperId,
            helperId = "ME",
            username = "Helper_${helperId.take(4)}",
            category = "Professionista",
            phoneNumber = "ND",
            isMeHelper = false,
            status = MatchStatus.ACCEPTED
        )
        matchDao.insertMatch(newMatch)

        // Avvisa lo UserViewModel di cambiare la scritta nella Home
        _requesterMatchEvents.emit(helperId)
    }
}
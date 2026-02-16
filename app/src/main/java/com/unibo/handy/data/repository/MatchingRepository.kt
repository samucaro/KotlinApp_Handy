package com.unibo.handy.data.repository

import android.util.Log
import com.google.gson.Gson
import com.unibo.handy.data.db.dao.MatchDAO
import com.unibo.handy.data.db.dao.StoredClientDAO
import com.unibo.handy.data.db.entity.MatchEntity
import com.unibo.handy.data.db.entity.MatchStatus
import com.unibo.handy.data.db.entity.ProfileData
import com.unibo.handy.data.db.entity.StoredClientEntity
import com.unibo.handy.data.network.ServiceAPI
import com.unibo.handy.data.network.WebSocketManager
import com.unibo.handy.data.network.dto.HelpRequestDTO
import com.unibo.handy.data.network.dto.TuplaDTO
import com.unibo.handy.domain.MatchingService
import com.unibo.handy.domain.PrivacyEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

/**
 * Repository centrale per il protocollo SamaritanCloud.
 * Gestisce:
 * 1. Ruolo Richiedente: Invio Help-Request (Beta+).
 * 2. Ruolo Service Client: Custodia profili offuscati e Calcolo Match
 */
class MatchingRepository(
    private val apiService: ServiceAPI,
    private val webSocketManager: WebSocketManager,
    private val matchDao: MatchDAO,
    private val storedClientDao: StoredClientDAO,
    private val locationRepo: LocationRepository,
    private val matchingService: MatchingService
) {
    private val gson: Gson = Gson()
    // Flow per notificare la UI di nuovi match
    private val _matchEvents = MutableSharedFlow<String>(replay = 0)
    val matchEvents = _matchEvents.asSharedFlow()

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

    // Gestione dei messaggi WebSocket specifici per il matching.
    suspend fun handleComputeMatch(payload: TuplaDTO?) {
        if (payload == null) return

        try {
            Log.d("MatchingRepo", "ComputeMatch received for: ${payload.t2TargetId}")

            // Delega al Domain Service la matematica pura
            val isCompatible = matchingService.verifyMatch(payload)

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
            category = "General", // Dovremmo recuperarla dal payload se presente
            phoneNumber = "ND",
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
    suspend fun handleStoreProfile(payload: Map<*, *>) = withContext(Dispatchers.IO) {
        try {
            // Parsing manuale della mappa (Gson converte i numeri in Double quindi devo fare il cast in Long)
            val targetId = payload["target_id"] as? String ?: return@withContext
            val rebX = (payload["reblurred_x"] as? Number)?.toLong() ?: 0L
            val rebY = (payload["reblurred_y"] as? Number)?.toLong() ?: 0L
            val userCategory = payload["category"] as? String ?: "Unknown"
            val userName = payload["username"] as? String ?: "Utente $targetId"

            val profileBlob = ProfileData(
                reblurredX = rebX,
                reblurredY = rebY,
                username = userName,
                category = userCategory,
                rating = 0
            )

            // Creazione dell'entità per il DB locale
            val entity = StoredClientEntity(
                clientId = targetId,
                profile = profileBlob
            )

            // Salvataggio nel DB
            storedClientDao.saveProfile(entity)

            Log.d("MatchingRepo", "Profile saved: $targetId ")

        } catch (e: Exception) {
            Log.e("MatchingRepo", "Error in handleStoreProfile", e)
        }
    }
}
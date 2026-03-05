package com.unibo.handy.data.repository

import com.google.gson.Gson
import com.unibo.handy.data.db.dao.MatchDAO
import com.unibo.handy.data.db.dao.StoredClientDAO
import com.unibo.handy.data.db.entity.MatchEntity
import com.unibo.handy.data.db.entity.MatchStatus
import com.unibo.handy.data.db.entity.ProfileData
import com.unibo.handy.data.db.entity.StoredClientEntity
import com.unibo.handy.data.network.WebSocketManager
import com.unibo.handy.data.network.dto.StoreProfileDTO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.util.UUID.randomUUID
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
    private val storedClientDao: StoredClientDAO
) {
    private val gson: Gson = Gson()
    // Flow per notificare la UI di nuovi match
    private val _matchEvents = MutableSharedFlow<Pair<String, String>>(replay = 0)
    val matchEvents = _matchEvents.asSharedFlow()

    private val _requesterMatchEvents = MutableSharedFlow<String>(replay = 0)
    val requesterMatchEvents = _requesterMatchEvents.asSharedFlow()

    suspend fun saveMatchToDb(requesterId: String, helperId: String) {
        val generatedMatchId = randomUUID().toString()

        val newMatch = MatchEntity(
            matchId = generatedMatchId,
            requesterId = requesterId,
            helperId = helperId,
            username = requesterId.take(4),
            category = "General",
            phoneNumber = "ND",
            isMeHelper = true,
            status = MatchStatus.PENDING
        )
        matchDao.insertMatch(newMatch)
        _matchEvents.emit(Pair(generatedMatchId, requesterId))
    }

    fun notifyServerOfMatch(requesterId: String, targetId: String) {
        val payloadMap = mapOf(
            "type" to "MATCH_FOUND",
            "payload" to mapOf(
                "requester_id" to requesterId,
                "target_id" to targetId
            )
        )

        val jsonString = gson.toJson(payloadMap)

        webSocketManager.sendMessage(jsonString)
    }

    // Gestisce il salvataggio dei profili offuscati da custodire per il matching
    suspend fun handleStoreProfile(payload: StoreProfileDTO) = withContext(Dispatchers.IO) {
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
    }

    suspend fun handleMatchFoundNotification(helperId: String) {
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

        _requesterMatchEvents.emit(helperId)
    }
}
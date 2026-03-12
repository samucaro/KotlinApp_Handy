package com.unibo.handy.data.network.strategy

import android.util.Log
import com.google.gson.Gson
import com.unibo.handy.data.network.dto.MatchFoundDTO
import com.unibo.handy.data.repository.MatchingRepository
import com.unibo.handy.service.notifications.NotificationHelper
import javax.inject.Inject

/**
 * Strategia invocata sul dispositivo del Richiedente (Requester)
 * quando un Helper ha risolto positivamente la sua Tupla di emergenza.
 */
class MatchFoundStrategy @Inject constructor(
    private val matchingRepo: MatchingRepository,
    private val notificationHelper: NotificationHelper,
    private val gson: Gson
) : MessageStrategy {

    override suspend fun handle(payload: String) {
        try {
            val dto = gson.fromJson(payload, MatchFoundDTO::class.java)
            matchingRepo.handleMatchFoundNotification(dto.targetId)

            notificationHelper.showMatchNotification(isHelper = false)
        } catch (e: Exception) {
            Log.e("MatchFoundStrategy", "Errore parsing JSON match found: ${e.message}")
        }
    }
}
package com.unibo.handy.data.network.strategy

import android.util.Log
import com.google.gson.Gson
import com.unibo.handy.data.network.dto.StoreProfileDTO
import com.unibo.handy.data.repository.MatchingRepository
import javax.inject.Inject

/**
 * Strategia invocata quando il server invia un profilo offuscato da custodire.
 * (Ruolo: Service Client / Helper).
 */
class StoreProfileStrategy @Inject constructor(
    private val matchingRepo: MatchingRepository,
    private val gson: Gson
) : MessageStrategy {

    override suspend fun handle(payload: String) {
        try {
            val dto = gson.fromJson(payload, StoreProfileDTO::class.java)
            matchingRepo.handleStoreProfile(dto)
        } catch (e: Exception) {
            Log.e("StoreProfileStrategy", "Error parsing JSON: ${e.message}")
        }
    }
}
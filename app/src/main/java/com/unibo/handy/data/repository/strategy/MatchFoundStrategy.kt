package com.unibo.handy.data.repository.strategy

import android.util.Log
import com.google.gson.Gson
import com.unibo.handy.data.network.dto.MatchFoundDTO
import com.unibo.handy.data.repository.MatchingRepository

class MatchFoundStrategy(
    private val matchingRepo: MatchingRepository,
    private val gson: Gson
) : MessageStrategy {

    override suspend fun handle(payload: String) {
        Log.d("MatchStrategy", "Ricevuta notifica di MATCH_FOUND dal server")

        // Estrapoliamo l'ID dell'helper dal payload
        val dto = gson.fromJson(payload, MatchFoundDTO::class.java)

        // Avvisiamo il Repository che l'Helper ha accettato!
        matchingRepo.handleMatchFoundNotification(dto.target_id)
    }
}
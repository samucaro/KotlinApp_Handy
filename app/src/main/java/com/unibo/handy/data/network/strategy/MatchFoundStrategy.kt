package com.unibo.handy.data.network.strategy

import android.util.Log
import com.google.gson.Gson
import com.unibo.handy.data.network.dto.MatchFoundDTO
import com.unibo.handy.data.repository.MatchingRepository

class MatchFoundStrategy(
    private val matchingRepo: MatchingRepository,
    private val gson: Gson
) : MessageStrategy {

    override suspend fun handle(payload: String) {
        val dto = gson.fromJson(payload, MatchFoundDTO::class.java)
        matchingRepo.handleMatchFoundNotification(dto.targetId)
    }
}
package com.unibo.handy.data.repository.strategy

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.unibo.handy.data.network.dto.TuplaDTO
import com.unibo.handy.data.repository.MatchingRepository

class ComputeMatchStrategy(
    private val matchingRepo: MatchingRepository,
    private val gson: Gson,
) : MessageStrategy {
    override suspend fun handle(payload: JsonElement) {
        Log.d("MatchStrategy", "Match compute message received")
        val dto = gson.fromJson(payload, TuplaDTO::class.java)
        matchingRepo.handleComputeMatch(dto)
    }
}
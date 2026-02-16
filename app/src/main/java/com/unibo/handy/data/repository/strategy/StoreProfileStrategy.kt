package com.unibo.handy.data.repository.strategy

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.unibo.handy.data.repository.MatchingRepository

// Strategia utilizzata per memorizzare/modificare i dati di un profilo presente nella memoria del
// service client
class StoreProfileStrategy(
    private val matchingRepository: MatchingRepository,
    private val gson: Gson
) : MessageStrategy {
    override suspend fun handle(payload: JsonElement) {
        Log.d("StoreProfileStrategy", "Profile update message received")
        val dto = gson.fromJson(payload, Map::class.java)
        matchingRepository.handleStoreProfile(dto)
    }
}
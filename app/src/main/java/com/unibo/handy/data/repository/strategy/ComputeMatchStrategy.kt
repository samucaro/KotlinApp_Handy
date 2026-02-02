package com.unibo.handy.data.repository.strategy

import com.google.gson.Gson
import com.unibo.handy.data.network.WebSocketManager
import com.unibo.handy.data.network.dto.TuplaDTO
import com.unibo.handy.domain.MatchingService

class ComputeMatchStrategy(
    private val matchingService: MatchingService,
    private val webSocketManager: WebSocketManager,
    private val gson: Gson
) : MessageStrategy {
    override suspend fun handle(fullMessage: Map<*, *>) {
        // 1. Estrazione del payload
        val payloadMap = fullMessage["payload"]

        // Converte la mappa in JSON stringa e poi nell'oggetto Tuple
        val jsonString = gson.toJson(payloadMap)
        val tupla = gson.fromJson(jsonString, TuplaDTO::class.java)

        // 2. Elaborazione
        val isMatch = matchingService.verifyMatch(tupla)

        // 3. Risposta (Solo se Match positivo)
        if (isMatch) {
            val response = mapOf(
                "type" to "MATCH_FOUND",
                "requester_id" to tupla.t1RequesterId, // T1
                "target_id" to tupla.t2TargetId        // T2
            )
            webSocketManager.sendMessage(gson.toJson(response))
        }
    }
}
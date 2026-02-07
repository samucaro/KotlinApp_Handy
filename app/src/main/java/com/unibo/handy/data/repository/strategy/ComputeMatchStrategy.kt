package com.unibo.handy.data.repository.strategy

import android.util.Log
import com.google.gson.Gson
import com.unibo.handy.data.network.WebSocketManager
import com.unibo.handy.data.network.dto.TuplaDTO
import com.unibo.handy.domain.MatchingService

class ComputeMatchStrategy(
    private val matchingService: MatchingService,
    private val webSocketManager: WebSocketManager,
    private val gson: Gson,
    private val onMatchDetected: (String) -> Unit
) : MessageStrategy {
    override suspend fun handle(fullMessage: Map<*, *>) {
        Log.d("MatchStrategy", "Ricevuta richiesta di calcolo MATCH!")
        try {
            // 1. Estrazione del payload
            val payloadMap = fullMessage["payload"]
            if (payloadMap == null) {
                Log.e("MatchStrategy", "Errore: Payload nullo")
                return
            }

            // Converte la mappa in JSON stringa e poi nell'oggetto Tuple
            val jsonString = gson.toJson(payloadMap)
            val tupla = gson.fromJson(jsonString, TuplaDTO::class.java)

            // 2. Elaborazione
            val isMatch = matchingService.verifyMatch(tupla)

            // 3. Risposta (Solo se Match positivo)
            if (isMatch) {
                // A. NOTIFICA LA UI
                onMatchDetected(tupla.t1RequesterId)

                // B. RISPOSTA AL SERVER
                val response = mapOf(
                    "type" to "MATCH_FOUND",
                    "requester_id" to tupla.t1RequesterId, // T1
                    "target_id" to tupla.t2TargetId        // T2
                )
                webSocketManager.sendMessage(gson.toJson(response))
            }
        } catch (e: Exception) {
            Log.e("MatchStrategy", "Errore durante il calcolo del match", e)
        }
    }
}
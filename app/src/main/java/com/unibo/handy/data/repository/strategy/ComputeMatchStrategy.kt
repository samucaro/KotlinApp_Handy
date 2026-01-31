package com.unibo.handy.data.repository.strategy

import android.util.Log
import com.google.gson.Gson
import com.unibo.handy.data.network.WebSocketManager
import com.unibo.handy.domain.MatchingService

class ComputeMatchStrategy(
    private val matchingService: MatchingService,
    private val webSocketManager: WebSocketManager,
    private val gson: Gson
) : MessageStrategy {
    override suspend fun handle(fullMessage: Map<*, *>) {
        val reqData = fullMessage["requester_data"] as? Map<*, *>

        if (reqData == null) {
            Log.e("MatchStrategy", "Missing data in the message COMPUTE_MATCH")
            return
        }

        val clientId = reqData["target_id"] as? String ?: return

        // 3. Calcolo (PrivacyEngine è un Object statico, quindi accessibile ovunque)
        val isMatch = matchingService.verifyMatch   (
            targetId = clientId,
            requestProfile = (reqData["beta_plus_x"] as Double).toLong(), // rblurredx, reblurredy, category
            encryptedBlurSum = (reqData["beta_plus_y"] as Double).toLong(),
            specificSumBlur = (reqData["term4_x"] as Double).toLong(),
            tolerance = (reqData["tolerance"] as Double).toLong()
        )

        if (isMatch) {
            val matchFoundMsg = mapOf("type" to "MATCH_FOUND", "target_id" to clientId)
            webSocketManager.sendMessage(gson.toJson(matchFoundMsg))
        }
    }
}
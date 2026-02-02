package com.unibo.handy.data.repository.strategy

import android.util.Log
import com.unibo.handy.data.db.dao.StoredClientDAO

// Strategia per aggiornare le nuove recensionio o valutazioni che vengono fatte
class UpdateRatindData(
    private val storedClientDao: StoredClientDAO
) : MessageStrategy {
    override suspend fun handle(fullMessage: Map<*, *>) {
        val payload = fullMessage["payload"] as? Map<*, *> ?: run {
            Log.e("StoreStrategy", "Missing target_id in the message UPDATE_RATINGDATA")
            return
        }
        val targetId = payload["target_id"] as? String ?: run {
            Log.e("StoreStrategy", "Missing target_id in the message UPDATE_RATINGDATA")
            return
        }

        val newAvg = payload["new_average"] as Int
        // Possibile aggiunta di recensioni
        // val newReviewsJson = payload["reviews_json"] as String

        storedClientDao.updateRatingData(targetId, newAvg)
    }
}
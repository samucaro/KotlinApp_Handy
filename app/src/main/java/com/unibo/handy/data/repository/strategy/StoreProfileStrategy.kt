package com.unibo.handy.data.repository.strategy

import android.util.Log
import com.unibo.handy.data.db.dao.StoredClientDAO
import com.unibo.handy.data.db.entity.StoredClientEntity

class StoreProfileStrategy(
    private val storedClientDao: StoredClientDAO
) : MessageStrategy {
    override suspend fun handle(fullMessage: Map<*, *>) {
        val payload = fullMessage["payload"] as? Map<*, *>
        if (payload == null) {
            Log.e("StoreStrategy", "Missing payload in the message STORE_PROFILE")
            return
        }

        val targetId = payload["target_uuid"] as String
        val reblurredX = (payload["reblurred_x"] as Double).toLong()
        val reblurredY = (payload["reblurred_y"] as Double).toLong()

        val entity = StoredClientEntity(
            clientId = targetId,
            reblurredX = reblurredX,
            reblurredY = reblurredY,
            category = payload["category"] as String
        )
        storedClientDao.saveProfile(entity)
        Log.d("StoreStrategy", "Saved profile: $targetId")
    }
}
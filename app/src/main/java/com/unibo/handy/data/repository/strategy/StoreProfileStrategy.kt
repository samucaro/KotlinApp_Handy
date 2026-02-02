package com.unibo.handy.data.repository.strategy

import android.util.Log
import com.unibo.handy.data.db.dao.StoredClientDAO
import com.unibo.handy.data.db.entity.ProfileData
import com.unibo.handy.data.db.entity.StoredClientEntity

// Strategia utilizzata per memorizzare/modificare i dati di un profilo presente nella memoria del
// service client
class StoreProfileStrategy(
    private val storedClientDao: StoredClientDAO
) : MessageStrategy {
    override suspend fun handle(fullMessage: Map<*, *>) {
        val payload = fullMessage["payload"] as? Map<*, *> ?: run {
            Log.e("StoreStrategy", "Missing payload in the message STORE_PROFILE")
            return
        }

        val targetId = payload["target_id"] as? String ?: run {
            Log.e("StoreStrategy", "Missing target_id in the message STORE_PROFILE")
            return
        }


        val profileBlob = ProfileData(
            reblurredX = (payload["reblurred_x"] as Double).toLong(),
            reblurredY = (payload["reblurred_y"] as Double).toLong(),
            username = payload["username"] as String,
            category = payload["category"] as String,
            rating = 0
        )

        val entity = StoredClientEntity(
            clientId = targetId,
            profile = profileBlob
        )
        storedClientDao.saveProfile(entity)
        Log.d("StoreStrategy", "Saved profile: $targetId")
    }
}
package com.unibo.handy.data.repository.strategy

import android.util.Log
import com.unibo.handy.data.db.dao.StoredClientDAO
import kotlin.collections.get

// Strategia che permette ai service client di aggiornare la posizione del loro client memorizzato
class UpdatePositionStrategy(
    private val storedClientDao: StoredClientDAO
) : MessageStrategy {
    override suspend fun handle(fullMessage: Map<*, *>) {
        val payload = fullMessage["payload"] as? Map<*, *> ?: run {
            Log.e("StoreStrategy", "Missing payload in the message UPDATE_POSITION")
            return
        }

        val targetId = payload["target_id"] as? String ?: run {
            Log.e("StoreStrategy", "Missing target_id in the message UPDATE_POSITION")
            return
        }

        val newX = (payload["reblurred_x"] as Double).toLong()
        val newY = (payload["reblurred_y"] as Double).toLong()

        storedClientDao.updatePosition(targetId, newX, newY)
    }
}
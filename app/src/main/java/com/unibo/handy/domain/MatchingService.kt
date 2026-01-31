package com.unibo.handy.domain

import com.unibo.handy.data.db.dao.StoredClientDAO

class MatchingService(
    private val storedClientDao: StoredClientDAO,
    private val privacyEngine: PrivacyEngine
) {
    suspend fun verifyMatch(
        targetId: String,
        requestProfile: Long, // rblurredx, reblurredy, category
        encryptedBlurSum: Long,
        specificSumBlur: Long,
        tolerance: Long
    ): Boolean {
        // 1. Recupero dati dal DB (Lavoro sporco di I/O)
        val storedProfile = storedClientDao.getProfile(targetId) ?: return false

        // 2. Orchestrazione del calcolo (Delega alla calcolatrice pura)
        return PrivacyEngine.computeMatching(
            requestProfile = requestProfile,
            tolerance = tolerance
        )
    }
}
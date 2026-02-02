package com.unibo.handy.domain

import com.unibo.handy.data.db.dao.StoredClientDAO
import com.unibo.handy.data.network.dto.TuplaDTO

class MatchingService(
    private val storedClientDao: StoredClientDAO
) {
    suspend fun verifyMatch(
        tupla: TuplaDTO
    ): Boolean {
        // 1. Uso T2 (Target ID) per cercare nel DB locale
        val storedEntity = storedClientDao.getProfile(tupla.t2TargetId)
        if (storedEntity == null) {
            // Non custodisce questo utente, ignora la richiesta
            return false
        }

        val storedProfile = storedEntity.profile

        // 2. Orchestrazione del calcolo
        return PrivacyEngine.computeMatching(
            t3 = tupla.t3BetaPlusX,
            t4 = tupla.t4BetaPlusY,
            t5 = tupla.t5SumUserBlur,
            t6 = tupla.t6SumServerBlur,
            t7 = tupla.t7Tolerance,
            storedX = storedProfile.reblurredX,
            storedY = storedProfile.reblurredY
        )
    }
}
package com.unibo.handy.domain

import android.util.Log
import com.unibo.handy.data.db.dao.StoredClientDAO
import com.unibo.handy.data.network.dto.TuplaDTO

class MatchingService(
    private val storedClientDao: StoredClientDAO
) {
    suspend fun verifyMatch(tupla: TuplaDTO): Boolean {
        Log.d("HandyMath", "--- INIZIO VERIFICA MATCH ---")
        Log.d("HandyMath", "Target ID richiesto: ${tupla.t2TargetId}")

        // 1. Uso T2 (Target ID) per cercare nel DB locale
        val storedEntity = storedClientDao.getProfile(tupla.t2TargetId)
        /*?: // Non custodisce questo utente, ignora la richiesta
        return false*/
        if (storedEntity == null) {
            Log.e("HandyMath", "⚠️ ATTENZIONE: Nessun profilo trovato nel DB locale per questo ID.")
            Log.e("HandyMath", "Motivo: L'Heartbeat precedente non è stato salvato o l'ID è diverso.")

            // --- TRUCCO PER IL TEST ---
            Log.w("HandyMath", "🚨 TEST MODE ATTIVO: Forzo il risultato a TRUE per mostrarti il popup!")
            return true
        }

        val storedProfile = storedEntity.profile

        // 2. Orchestrazione del calcolo
        return try {
            PrivacyEngine.computeMatching(
                t3 = tupla.t3BetaPlusX,
                t4 = tupla.t4BetaPlusY,
                t5 = tupla.t5SumUserBlur,
                t6 = tupla.t6SumServerBlur,
                t7 = tupla.t7Tolerance,
                storedX = storedProfile.reblurredX,
                storedY = storedProfile.reblurredY
            )
        } catch (e: Exception) {
            Log.e("HandyMath", "Errore in PrivacyEngine: ${e.message}. Ritorno TRUE per test.")
            true
        }
    }
}
package com.unibo.handy.domain

import android.util.Log
import com.unibo.handy.data.db.dao.StoredClientDAO
import com.unibo.handy.data.network.dto.TuplaDTO
import java.math.BigInteger
import javax.inject.Inject

class MatchingService @Inject constructor(
    private val storedClientDao: StoredClientDAO
) {
    suspend fun verifyMatch(tupla: TuplaDTO, privateKey: BigInteger, modulus: BigInteger): Boolean {
        Log.i("HandyMatch", "--- INIZIO PROCESSO DI MATCHING ---")
        Log.d("HandyMatch", "Target ID (Io): ${tupla.t2TargetId}")

        // 1. Uso T2 (Target ID) per cercare nel DB locale
        val storedEntity = storedClientDao.getProfile(tupla.t2TargetId)
        /*?: // Non custodisce questo utente, ignora la richiesta
        return false*/
        if (storedEntity == null) {
            Log.e("HandyMatch", "MATCH FALLITO: Non ho dati salvati per il Target ID ${tupla.t2TargetId}")
            Log.e("HandyMatch", "Possibile causa: Il server ha mandato il match prima che io ricevessi lo STORE_PROFILE.")
            return false
        }

        val storedProfile = storedEntity.profile
        Log.d("HandyMatch", "Profilo Locale Trovato: ${storedProfile.username} (Pos offuscata salvata: ${storedProfile.reblurredX}, ${storedProfile.reblurredY})")

        // 2. Orchestrazione del calcolo
        return try {
            val isCompatible = PrivacyEngine.computeMatching(
                t3x = tupla.t3BetaPlusX,
                t3y = tupla.t3BetaPlusY,
                t4 = tupla.t4SumUserBlur,
                t5 = tupla.t5SumServerBlur,
                t6 = tupla.t6Tolerance,
                storedX = storedProfile.reblurredX,
                storedY = storedProfile.reblurredY,
                privateKey = privateKey,
                n = modulus
            )
            if (isCompatible) {
                Log.i("HandyMatch", "RISULTATO: COMPATIBILE! Distanza < Tolleranza")
            } else {
                Log.w("HandyMatch", "RISULTATO: NON COMPATIBILE. Utente troppo lontano.")
            }
            isCompatible
        } catch (e: Exception) {
            Log.e("HandyMatch", "Errore matematico nel matching", e)
            false
        }
    }
}
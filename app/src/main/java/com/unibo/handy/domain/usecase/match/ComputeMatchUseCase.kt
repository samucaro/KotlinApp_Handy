package com.unibo.handy.domain.usecase.match

import android.util.Log
import com.unibo.handy.data.db.dao.StoredClientDAO
import com.unibo.handy.data.network.dto.TupleDTO
import com.unibo.handy.data.repository.SecureKeyRepository
import com.unibo.handy.domain.crypto.PrivacyEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Orchestratore per la Fase 4 del protocollo: Distance-Computation.
 * Risolve la tupla matematica ricevuta dal server confrontandola con il database locale.
 */
class ComputeMatchUseCase @Inject constructor(
    private val secureKeyRepository: SecureKeyRepository,
    private val storedClientDao: StoredClientDAO
) {
    suspend operator fun invoke(
        tupla: TupleDTO
    ): Boolean = withContext(Dispatchers.IO) {

        val privateKey = secureKeyRepository.getPrivateKey() ?: throw Exception("Chiave privata mancante")
        val modulus = secureKeyRepository.getPublicModulus() ?: throw Exception("Modulo pubblico mancante")

        // Recupera il profilo offuscato (aggiornato in precedenza via Profile-Update)
        val storedEntity = storedClientDao.getProfile(tupla.t2TargetId)
        if (storedEntity == null) {
            Log.e("HandyMatch", "MATCH FALLITO: Non ho dati salvati per il Target ID ${tupla.t2TargetId}")
            return@withContext false
        }
        val storedProfile = storedEntity.profile

        return@withContext try {
            // Esecuzione del controllo crittografico sulla distanza
            val result = PrivacyEngine.computeMatching(
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
            Log.d("STRATEGY_MATCH", "Risultato crittografico dal Privacy Engine: $result")
            Log.d("STRATEGY_MATCH", "Modulo: $modulus")
            result
        } catch (e: Exception) {
            Log.e("STRATEGY_MATCH", "CRASH Matematico nel PrivacyEngine: ${e.message}", e)
            false
        }
    }
}
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
 * Use Case che orchestra il calcolo della distanza euclidea su campo finito.
 */
class ComputeMatchUseCase @Inject constructor(
    private val secureKeyRepository: SecureKeyRepository,
    private val storedClientDao: StoredClientDAO
) {
    suspend operator fun invoke(
        tupla: TupleDTO
    ): Boolean = withContext(Dispatchers.IO) {
        // 1. Recupero le chiavi crittografiche
        val privateKey = secureKeyRepository.getPrivateKey() ?: throw Exception("Chiave privata mancante")
        val modulus = secureKeyRepository.getPublicModulus() ?: throw Exception("Modulo pubblico mancante")

        // 2. Recupero il profilo offuscato salvato in precedenza
        val storedEntity = storedClientDao.getProfile(tupla.t2TargetId)
        if (storedEntity == null) {
            Log.e("HandyMatch", "MATCH FALLITO: Non ho dati salvati per il Target ID ${tupla.t2TargetId}")
            return@withContext false
        }
        val storedProfile = storedEntity.profile

        // 3. Eseguo il calcolo matematico sul PrivacyEngine
        return@withContext try {
            PrivacyEngine.computeMatching(
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
        } catch (_: Exception) {
            false // Ritorna falso in caso di errori crittografici
        }
    }
}
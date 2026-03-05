package com.unibo.handy.domain.usecase.match

import com.unibo.handy.data.repository.LocationRepository
import com.unibo.handy.data.repository.SecureKeyRepository
import com.unibo.handy.data.repository.UserRepository
import com.unibo.handy.domain.crypto.PaillierEncryption
import com.unibo.handy.domain.crypto.PrivacyEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigInteger
import javax.inject.Inject

class SendHelpRequestUseCase @Inject constructor(
    private val locationRepository: LocationRepository,
    private val secureKeyRepository: SecureKeyRepository,
    private val userRepository: UserRepository
) {
    /**
     * FASE 3: HELP-REQUEST
     * Metodo di invio richiesta di aiuto usa il canale Retrofit REST
     */
    suspend operator fun invoke(
        userId: String,
        category: String,
        tolerance: Double
    ) = withContext(Dispatchers.IO) {
        val location = locationRepository.getCurrentLocation() ?: throw Exception("GPS non disponibile")
        val modulus = secureKeyRepository.getPublicModulus() ?: throw Exception("Modulo pubblico mancante")

        // 1. Blurring Matematico
        val blurredData = PrivacyEngine.createHelpRequest(
            lat = location.latitude,
            lon = location.longitude,
            tol = tolerance
        )

        // 2. Cifratura Paillier
        val cipherBlur = PaillierEncryption.encrypt(BigInteger.valueOf(blurredData.encryptedR), modulus)
        val cipherTol = PaillierEncryption.encrypt(BigInteger.valueOf(blurredData.encryptedTol), modulus)

        // 3. Passa i dati crittografati al Repository per l'invio di rete
        userRepository.postHelpRequestToNetwork(
            userId = userId,
            category = category,
            blurredX = blurredData.betaPlusX,
            blurredY = blurredData.betaPlusY,
            encryptedR = cipherBlur.toString(),
            encryptedTol = cipherTol.toString(),
            publicModulus = modulus.toString()
        )
    }
}
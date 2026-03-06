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

/**
 * Orchestratore per la Fase 3 del protocollo: Help-Request.
 * Prepara la query spaziale cifrata del richiedente per innescare la ricerca di match.
 */
class SendHelpRequestUseCase @Inject constructor(
    private val locationRepository: LocationRepository,
    private val secureKeyRepository: SecureKeyRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(
        userId: String,
        category: String,
        tolerance: Double
    ) = withContext(Dispatchers.IO) {
        val location = locationRepository.getCurrentLocation() ?: throw Exception("GPS non disponibile")
        val modulus = secureKeyRepository.getPublicModulus() ?: throw Exception("Modulo pubblico mancante")

        // 1. Calcolo coordinate query e rumore spaziale
        val blurredData = PrivacyEngine.createHelpRequest(
            lat = location.latitude,
            lon = location.longitude,
            tol = tolerance
        )

        // 2. Cifratura Omomorfica del rumore e della tolleranza
        val cipherBlur = PaillierEncryption.encrypt(BigInteger.valueOf(blurredData.encryptedR), modulus)
        val cipherTol = PaillierEncryption.encrypt(BigInteger.valueOf(blurredData.encryptedTol), modulus)

        // 3. Invio asincrono al server
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
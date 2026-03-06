package com.unibo.handy.domain.usecase.profile

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
 * Orchestratore per la Fase 2 del protocollo: Profile-Update-Request.
 * Recupera la posizione, applica l'offuscamento spaziale e invia i dati al server.
 */
class SendHeartbeatUseCase @Inject constructor(
    private val locationRepository: LocationRepository,
    private val secureKeyRepository: SecureKeyRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke() = withContext(Dispatchers.IO) {
        val user = userRepository.getCurrentUserSnapshot()
        if (user == null || !user.helpModeActive || user.category == "Generico") return@withContext

        val location = locationRepository.getCurrentLocation() ?: throw Exception("GPS non disponibile")
        val modulus = secureKeyRepository.getPublicModulus() ?: throw Exception("Modulo pubblico mancante")

        // 1. Blurring Matematico (Offuscamento coordinate base)
        val blurredData = PrivacyEngine.createEncryptedData(
            lat = location.latitude,
            lon = location.longitude
        )

        // 2. Cifratura Paillier del rumore spaziale
        val encryptedBlur = PaillierEncryption.encrypt(BigInteger.valueOf(blurredData.encryptedR), modulus)

        // 3. Trasmissione al livello Dati (Rete)
        locationRepository.postHeartbeatToNetwork(
            userId = user.userId,
            blurredX = blurredData.betaMinusX,
            blurredY = blurredData.betaMinusY,
            encryptedBlur = encryptedBlur.toString()
        )
    }
}
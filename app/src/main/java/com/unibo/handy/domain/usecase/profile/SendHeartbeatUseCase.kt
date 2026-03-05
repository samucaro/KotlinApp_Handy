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

class SendHeartbeatUseCase @Inject constructor(
    private val locationRepository: LocationRepository,
    private val secureKeyRepository: SecureKeyRepository,
    private val userRepository: UserRepository
) {
    /**
     *FASE 2: PROFILE-UPDATE-REQUEST
     *Solo per Helper client, usa il canale Retrofit REST
     **/
    suspend operator fun invoke() = withContext(Dispatchers.IO) {
        val user = userRepository.getCurrentUserSnapshot()
        if (user == null || !user.helpModeActive || user.category == "Generico") return@withContext

        val location = locationRepository.getCurrentLocation() ?: throw Exception("GPS non disponibile")
        val modulus = secureKeyRepository.getPublicModulus() ?: throw Exception("Modulo pubblico mancante")

        // 1. Blurring Matematico
        val blurredData = PrivacyEngine.createEncryptedData(
            lat = location.latitude,
            lon = location.longitude
        )

        // 2. Cifratura Paillier
        val encryptedBlur = PaillierEncryption.encrypt(BigInteger.valueOf(blurredData.encryptedR), modulus)

        // 3. Passa i dati crittografati al Repository per l'invio di rete
        locationRepository.postHeartbeatToNetwork(
            userId = user.userId,
            blurredX = blurredData.betaMinusX,
            blurredY = blurredData.betaMinusY,
            encryptedBlur = encryptedBlur.toString()
        )
    }
}
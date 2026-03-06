package com.unibo.handy.domain.crypto

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigInteger
import java.security.SecureRandom
import kotlin.math.sqrt

/**
 * Motore crittografico che implementa la logica di Blurring Spaziale
 * e le formule di Distance Computation del protocollo SamaritanCloud.
 */
object PrivacyEngine {
    // P: Un grande numero primo che definisce la dimensione del campo finito Zp.
    private const val P: Long = 999999937L

    // Precisione: 10^7 mantiene la precisione GPS al centimetro
    private const val PRECISION = 10_000_000.0
    private val secureRandom = SecureRandom()

    data class UpdateProfileData(
        val betaMinusX: Long,
        val betaMinusY: Long,
        val encryptedR: Long
    )

    data class HelpRequestData(
        val betaPlusX: Long,
        val betaPlusY: Long,
        val encryptedR: Long,
        val encryptedTol: Long
    )

    /**
     * FASE 2: PROFILE-UPDATE-REQUEST
     * Offusca la posizione dell'Helper applicando un rumore spaziale negativo.
     */
    fun createEncryptedData(lat: Double, lon: Double): UpdateProfileData {
        val pX = toFixedPoint(lat)
        val pY = toFixedPoint(lon)

        val personalizedBlur = generateNoise()

        // Formula: β- = (p - r) mod P
        val blurredX = modSub(pX, personalizedBlur)
        val blurredY = modSub(pY, personalizedBlur)

        Log.v("HandyCrypto", "UPDATE-REQUEST -> Lat/Lon Fixed: ($pX, $pY) | Rumore: $personalizedBlur")

        return UpdateProfileData(blurredX, blurredY, personalizedBlur)
    }

    /**
     * FASE 3: HELP-REQUEST
     * Offusca la posizione del Requester applicando un rumore spaziale positivo.
     */
    fun createHelpRequest(lat: Double, lon: Double, tol: Double): HelpRequestData {
        val pX = toFixedPoint(lat)
        val pY = toFixedPoint(lon)

        val personalizedBlur = generateNoise()

        // Formula: β+ = (p + r) mod P
        val blurredX = modAdd(pX, personalizedBlur)
        val blurredY = modAdd(pY, personalizedBlur)
        val tolerance = tol.toLong()

        Log.v("HandyCrypto", "HELP-REQUEST -> Lat/Lon Fixed: ($pX, $pY) | Rumore: $personalizedBlur")

        return HelpRequestData(blurredX, blurredY, personalizedBlur, tolerance)
    }

    /**
     * FASE: DISTANCE COMPUTATION
     * Verifica la prossimità spaziale annullando i rumori matematici.
     */
    suspend fun computeMatching(
        t3x: Long, t3y: Long, // β+ (Coordinate offuscate del richiedente + R_Global)
        t4: String, // E(r_req + r_target) (Somma omomorfica dei rumori utente)
        t5: Long, // ^{cs}r_req + ^{cs}r_target (Somma dei rumori server)
        t6: String, // E(Tolleranza)
        storedX: Long, // β- (Coordinate ri-offuscate dell'helper)
        storedY: Long,
        privateKey: BigInteger,
        n: BigInteger
    ): Boolean = withContext(Dispatchers.Default) {

        // 1. DECIFRATURA (Inversione dell'omomorfismo di Paillier)
        val t4Decrypted = PaillierEncryption.decrypt(BigInteger(t4), n, privateKey).toLong()
        val toleranceDecrypted = PaillierEncryption.decrypt(BigInteger(t6), n, privateKey).toLong()

        // 2. CALCOLO ASSE X (Annullamento dei rumori sul campo finito Zp)
        val rawDiffX = modSub(t3x, storedX)
        var cleanDeltaX = modSub(rawDiffX, t4Decrypted)
        cleanDeltaX = modSub(cleanDeltaX, t5)
        val metricX = minMetricDistance(cleanDeltaX)

        // 3. CALCOLO ASSE Y
        val rawDiffY = modSub(t3y, storedY)
        var cleanDeltaY = modSub(rawDiffY, t4Decrypted)
        cleanDeltaY = modSub(cleanDeltaY, t5)
        val metricY = minMetricDistance(cleanDeltaY)

        // 4. CALCOLO DISTANZA EUCLIDEA
        val distSquared = (metricX * metricX) + (metricY * metricY)
        val distance = sqrt(distSquared.toDouble())

        Log.d("HandyCrypto", "Distance calculated: $distance | Decrypted tolerance: $toleranceDecrypted")

        // 5. VERIFICA SOGLIA ---
        distance <= toleranceDecrypted
    }

    // --- FUNZIONI MATEMATICHE DI SUPPORTO (Campo Finito Zp) ---
    private fun toFixedPoint(value: Double): Long {
        return (value * PRECISION).toLong()
    }

    private fun generateNoise(): Long {
        return (secureRandom.nextLong() and Long.MAX_VALUE) % P
    }

    private fun modAdd(a: Long, b: Long): Long {
        return ((a % P) + (b % P)) % P
    }

    private fun modSub(a: Long, b: Long): Long {
        val res = (a % P) - (b % P)
        return if (res < 0) res + P else res
    }

    private fun minMetricDistance(delta: Long): Long {
        return if (delta > P / 2) P - delta else delta
    }
}
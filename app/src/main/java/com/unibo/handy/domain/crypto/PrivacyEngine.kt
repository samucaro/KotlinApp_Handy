package com.unibo.handy.domain.crypto

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigInteger
import java.security.SecureRandom
import kotlin.math.sqrt

/**
 * Motore crittografico che implementa la logica di Blurring di SamaritanCloud.
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
     * Implementa la logica: β- = (Coordinate - Blur) mod P
     */
    fun createEncryptedData(lat: Double, lon: Double): UpdateProfileData {
        // 1. Conversione in Fixed Point
        val pX = toFixedPoint(lat)
        val pY = toFixedPoint(lon)

        // 2. Generazione Rumore Casuale (r)
        val personalizedBlur = generateNoise()

        // 3. Calcolo Beta Minus
        // Formula: β- = (p - r) mod P
        val blurredX = modSub(pX, personalizedBlur)
        val blurredY = modSub(pY, personalizedBlur)

        // LOG DI VERIFICA MATEMATICA
        Log.v("HandyCrypto", """
        BLURRING UPDATE:
        Lat Reale: $lat -> Fixed: $pX
        Rumore (r): $personalizedBlur
        Beta- (Inviato): $blurredX
        """.trimIndent())

        return UpdateProfileData(blurredX, blurredY, personalizedBlur)
    }

    /**
     * FASE 3: HELP-REQUEST
     * Implementa la logica: β+ = (Coordinate + Rumore) mod P
     */
    fun createHelpRequest(lat: Double, lon: Double, tol: Double): HelpRequestData {
        val pX = toFixedPoint(lat)
        val pY = toFixedPoint(lon)

        val personalizedBlur = generateNoise()

        // Calcolo Beta Plus
        // Formula: β+ = (p + r) mod P
        val blurredX = modAdd(pX, personalizedBlur)
        val blurredY = modAdd(pY, personalizedBlur)
        val tolerance = tol.toLong()

        // LOG DI VERIFICA MATEMATICA
        Log.v("HandyCrypto", """
        BLURRING HELP:
        Lat Reale: $lat -> Fixed: $pX
        Rumore (r): $personalizedBlur
        Beta+ (Inviato): $blurredX
        """.trimIndent())

        return HelpRequestData(blurredX, blurredY, personalizedBlur, tolerance)
    }

    /**
     * FASE 3: MATCHING (Ruolo: Service Client)
     * Verifica se la distanza è < tolleranza rimuovendo il rumore
     */
    suspend fun computeMatching(
        // Dati dalla Tupla
        t3x: Long, t3y: Long,
        t4: String,
        t5: Long,
        t6: String,

        // Dati dal DB Locale
        storedX: Long,
        storedY: Long,

        privateKey: BigInteger,
        n: BigInteger
    ): Boolean = withContext(Dispatchers.Default) {

        // 1. DECIFRATURA OMOMORFICA (Paillier)
        val t5DecryptedBigInt = PaillierEncryption.decrypt(BigInteger(t4), n, privateKey)
        val toleranceBigInt = PaillierEncryption.decrypt(BigInteger(t6), n, privateKey)

        val t4Decrypted = t5DecryptedBigInt.toLong()
        val toleranceDecrypted = toleranceBigInt.toLong()

        // -----------------------------------------------------------------------------------------


        // 2. CALCOLO ASSE X: (T3 - StoredX - T4Decrypted - T5)
        val rawDiffX = modSub(t3x, storedX)
        var cleanDeltaX = modSub(rawDiffX, t4Decrypted)
        cleanDeltaX = modSub(cleanDeltaX, t5)
        val metricX = minMetricDistance(cleanDeltaX)

        // 3. CALCOLO ASSE Y: (T3 - StoredY - T4Decrypted - T5)
        val rawDiffY = modSub(t3y, storedY)
        var cleanDeltaY = modSub(rawDiffY, t4Decrypted)
        cleanDeltaY = modSub(cleanDeltaY, t5)
        val metricY = minMetricDistance(cleanDeltaY)

        // -----------------------------------------------------------------------------------------


        // 4. CALCOLO DISTANZA EUCLIDEA

        val distSquared = (metricX * metricX) + (metricY * metricY)
        val distance = sqrt(distSquared.toDouble())
        Log.d("HandyCrypto", "Distance calculated: $distance | Decrypted tolerance: $toleranceDecrypted")

        // 5. VERIFICA SOGLIA ---
        distance <= toleranceDecrypted
    }

    // --- FUNZIONI MATEMATICHE DI SUPPORTO ---
    private fun toFixedPoint(value: Double): Long {
        return (value * PRECISION).toLong()
    }
    private fun generateNoise(): Long {
        // Usa l'and bit a bit per evitare l'overflow negativo di Math.abs su Long.MIN_VALUE
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
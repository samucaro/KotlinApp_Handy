package com.unibo.handy.domain

import java.security.SecureRandom
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Motore crittografico che implementa la logica di Blurring di SamaritanCloud.
 *
 * Riferimenti al Paper:
 * - Update Logic (Data Owner): Generazione di Beta Minus (β-)
 * - Request Logic (Query User): Generazione di Beta Plus (β+)
 * - Matching Logic (Service Client): Calcolo distanza euclidea su campo finito
 */
object PrivacyEngine {

    // 1. COSTANTI DEL SISTEMA
    // P: Un grande numero primo che definisce la dimensione del campo finito Zp.
    // Deve essere condiviso da tutti i client e dal server.
    // In produzione si usa un numero molto grande (es. 2048 bit), qui ne usiamo uno che sta in un Long.
    private const val P: Long = 999999937L

    // Precisione: 10^7 mantiene la precisione GPS al centimetro trasformando i Double in Long.
    private const val PRECISION = 10_000_000.0

    // Generatore di numeri casuali sicuro per il rumore 'r'
    private val secureRandom = SecureRandom()

    // --- DATA CLASS PER I RISULTATI ---
    /**
     * Risultato dell'algoritmo di Update (Heartbeat).
     * Contiene la posizione offuscata con sottrazione del rumore.
     */
    data class UpdateProfileData(
        val betaMinusX: Long, // (x - rX) mod P
        val betaMinusY: Long, // (y - rY) mod P
        val encryptedR: Long // personalizedBlur (Da cifrare con Paillier)
    )

    /**
     * Risultato dell'algoritmo di Richiesta Aiuto.
     * Contiene la posizione offuscata con addizione del rumore.
     */
    data class HelpRequestData(
        val betaPlusX: Long, // (x + rX) mod P
        val betaPlusY: Long, // (y + rY) mod P
        val encryptedR: Long, // rX (Da cifrare con Paillier)
        val encryptedTol: Long
    )

    // --- FUNZIONI CORE (ALGORITMI DEL PAPER) ---
    /**
     * FASE 2: PROFILE-UPDATE-REQUEST (Fig. 4b paper)
     * Implementa la logica: β- = (Coordinate - Blur) mod P
     */
    fun createEncryptedData(lat: Double, lon: Double): UpdateProfileData {
        // 1. Conversione in Fixed Point (Interi Long)
        val pX = toFixedPoint(lat)
        val pY = toFixedPoint(lon)

        // 2. Generazione Rumore Casuale (r)
        val personalizedBlur = generateNoise()

        // 3. Calcolo Beta Minus (Sottrazione Modulare)
        // Formula: β- = (p - r) mod P
        val blurredX = modSub(pX, personalizedBlur)
        val blurredY = modSub(pY, personalizedBlur)

        return UpdateProfileData(blurredX, blurredY, personalizedBlur)
    }

    /**
     * FASE 3: HELP-REQUEST (Fig. 5b paper)
     * Implementa la logica: β+ = (Coordinate + Rumore) mod P
     */
    fun createHelpRequest(lat: Double, lon: Double, tol: Double): HelpRequestData {
        // 1. Conversione in Fixed Point
        val pX = toFixedPoint(lat)
        val pY = toFixedPoint(lon)

        // 2. Generazione Rumore Casuale (r)
        val personalizedBlur = generateNoise() //Da cifrare con Paillier

        // 3. Calcolo Beta Plus (Addizione Modulare)
        // Formula: β+ = (p + r) mod P
        val blurredX = modAdd(pX, personalizedBlur)
        val blurredY = modAdd(pY, personalizedBlur)

        // 4. Calcolo tolleranza cifrata
        // Formula:
        val tolerance = tol.toLong() //Da cifrare con Paillier

        return HelpRequestData(blurredX, blurredY, personalizedBlur, tolerance)
    }

    /**
     * FASE 3: MATCHING (Ruolo: Service Client)
     * Verifica se la distanza è < tolleranza rimuovendo il rumore.
     */
    fun computeMatching(
        // Dati dalla Tupla
        t3: Long, t4: Long, // Beta+ X, Y; saranno con Paillier
        t5: Long, // Somma Blur Utenti, saranno con Paillier
        t6: Long, // Somma Blur Server
        t7: Long, // Tolleranza sarà con Paillier

        // Dati dal DB Locale
        storedX: Long, // Coordinata X reblurrata del client posseduto sarà con Paillier
        storedY: Long  // Coordinata Y reblurrata del client posseduto sarà con Paillier
    ): Boolean {
        // --- CALCOLO ASSE X ---
        // Formula: (T3 - StoredX - T5 - T6)

        // 1. Differenza tra le coordinate offuscate (T3 - StoredX)
        val rawDiffX = modSub(t3, storedX)

        // 2. Rimozione dei rumori ( - T5 - T6 )
        // Rimuovo il blur del server
        var cleanDeltaX = modSub(rawDiffX, t5)
        // Rimuovo il blur degli utenti
        cleanDeltaX = modSub(cleanDeltaX, t6)

        // 3. Gestione distanza minima sull'anello (Wrapping)
        val metricX = minMetricDistance(cleanDeltaX)


        // --- CALCOLO ASSE Y ---
        // Formula: (T4 - StoredY - T5 - T6)

        val rawDiffY = modSub(t4, storedY)
        var cleanDeltaY = modSub(rawDiffY, t5)
        cleanDeltaY = modSub(cleanDeltaY, t6)

        val metricY = minMetricDistance(cleanDeltaY)


        // --- CALCOLO DISTANZA EUCLIDEA ---
        // Sqrt( x^2 + y^2 )
        val distSquared = (cleanDeltaX * cleanDeltaX) + (cleanDeltaY * cleanDeltaY)
        val distance = sqrt(distSquared.toDouble())

        // --- VERIFICA SOGLIA ---
        // Distanza <= T7
        return distance <= t7
    }

    // --- FUNZIONI MATEMATICHE DI SUPPORTO ---
    private fun toFixedPoint(value: Double): Long {
        return (value * PRECISION).toLong()
    }

    private fun generateNoise(): Long {
        // Genera un numero positivo tra 0 e P-1
        return abs(secureRandom.nextLong()) % P
    }

    /**
     * Somma Modulare Sicura: (a + b) mod P
     */
    private fun modAdd(a: Long, b: Long): Long {
        return ((a % P) + (b % P)) % P
    }

    /**
     * Sottrazione Modulare Sicura: (a - b) mod P
     * In Java/Kotlin il % può restituire negativi, quindi aggiungiamo P.
     */
    private fun modSub(a: Long, b: Long): Long {
        val res = (a % P) - (b % P)
        return if (res < 0) res + P else res
    }

    /**
     * Calcola la distanza minima su un anello circolare.
     * Necessario perché lavoriamo in Zp.
     */
    private fun minMetricDistance(delta: Long): Long {
        return if (delta > P / 2) {
            P - delta
        } else {
            delta
        }
    }
}
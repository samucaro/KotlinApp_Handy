package com.unibo.handy.domain

import java.security.SecureRandom
import kotlin.math.abs

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
        val plainNoiseX: Long, // rX (Da cifrare con Paillier)
        val plainNoiseY: Long  // rY
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
     * FASE 3: HELP-REQUEST (Ruolo: Query User / Richiedente)
     * Implementa la logica: β+ = (Coordinate + Rumore) mod P
     */
    fun createHelpRequest(lat: Double, lon: Double): HelpRequestData {
        // 1. Conversione in Fixed Point
        val pX = toFixedPoint(lat)
        val pY = toFixedPoint(lon)

        // 2. Generazione Rumore Casuale (r)
        val rX = generateNoise()
        val rY = generateNoise()

        // 3. Calcolo Beta Plus (Addizione Modulare)
        // Formula: β+ = (p + r) mod P
        val betaPlusX = modAdd(pX, rX)
        val betaPlusY = modAdd(pY, rY)

        return HelpRequestData(betaPlusX, betaPlusY, rX, rY)
    }

    /**
     * FASE 3: MATCHING (Ruolo: Service Client)
     * Verifica se la distanza è < tolleranza rimuovendo il rumore.
     *
     * @param betaPlus Coordinata ricevuta dalla richiesta (β+)
     * @param betaMinus Coordinata salvata nel DB (β-)
     * @param serverCorrectionTerm Il valore inviato dal server (r_req + r_target + cs_r)
     * @param myCsr Il "Client Specific Random" che il Service Client possiede (cs_r)
     * @param toleranceSquared Tolleranza al quadrato (già convertita in unità fisse)
     */
    fun checkDistance(
        betaPlus: Long,
        betaMinus: Long,
        serverCorrectionTerm: Long,
        myCsr: Long,
        toleranceSquared: Long
    ): Boolean {
        // La formula matematica del paper per ottenere la distanza pulita (Delta):
        // Delta = (β+ - β-) - (serverCorrectionTerm - cs_r)

        // 1. Calcolo la differenza grezza tra le coordinate offuscate
        // rawDiff = (x_req + r_req) - (x_target - r_target)
        val rawDiff = modSub(betaPlus, betaMinus)

        // 2. Calcolo il rumore totale che il server ha aggregato (ma che devo pulire dal mio cs_r)
        // noiseInfo = (r_req + r_target + cs_r) - cs_r  => Rimane (r_req + r_target)
        val totalNoise = modSub(serverCorrectionTerm, myCsr)

        // 3. Sottraggo il rumore totale dalla differenza grezza
        // cleanDiff = rawDiff - totalNoise
        // cleanDiff = (x_req - x_target + r_req + r_target) - (r_req + r_target)
        // cleanDiff = x_req - x_target (Distanza Reale!)
        val cleanDelta = modSub(rawDiff, totalNoise)

        // 4. Gestione della distanza minima sull'anello (Modular Distance)
        // Se P=100, la distanza tra 98 e 2 è 4, non 96.
        val metricDistance = minMetricDistance(cleanDelta)

        // 5. Verifica soglia (Usiamo i quadrati per evitare radici quadrate lente)
        return (metricDistance * metricDistance) <= toleranceSquared
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
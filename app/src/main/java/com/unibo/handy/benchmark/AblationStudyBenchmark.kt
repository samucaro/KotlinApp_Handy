package com.unibo.handy.benchmark

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.unibo.handy.domain.crypto.PaillierEncryption
import java.math.BigInteger
import kotlin.system.measureNanoTime

// Data class formattata per l'esportazione CSV/Excel
data class BenchmarkResult(
    val levelName: String,
    val avgExecutionTimeMs: Double,
    val estimatedPayloadSizeBytes: Int
)

object AblationStudyBenchmark {

    private const val TAG = "SamaritanBenchmark"
    private const val ITERATIONS = 100

    // Dati fittizi (Coordinate GPS in fixed point)
    private val plainTextLocationA = BigInteger.valueOf(444900000)
    private val plainTextLocationB = BigInteger.valueOf(444900100)
    private val P = BigInteger.valueOf(999999937)

    // Genera una chiave a 1024 bit SOLO per questo test (simula il vero carico)
    private val keys = PaillierEncryption.keygen()
    private val pubKey = keys.first
    private val privKey = keys.second
    private val nSquared = pubKey * pubKey

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun runFullStudy(): List<BenchmarkResult> {
        Log.i(TAG, "--- INIZIO ABLATION STUDY (1024-bit) ---")
        warmUpJvm() // Fondamentale in Java/Kotlin per attivare il compilatore JIT

        val results = mutableListOf<BenchmarkResult>()

        results.add(testLevel0Plaintext())
        results.add(testLevel1BlurOnly())
        results.add(testLevel2PaillierOnly())
        results.add(testLevel3FullSamaritanCloud())

        Log.i(TAG, "--- FINE ABLATION STUDY ---")

        // Stampa i risultati in formato CSV nei log per Excel
        Log.i(TAG, "=== RISULTATI ESPORTABILI CSV ===")
        Log.i(TAG, "Configurazione, TempoMedio(ms), Payload(Bytes)")
        results.forEach {
            Log.i(TAG, "${it.levelName}, ${String.format("%.4f", it.avgExecutionTimeMs)}, ${it.estimatedPayloadSizeBytes}")
        }

        return results
    }

    private fun warmUpJvm() {
        Log.d(TAG, "JVM Warm-up in corso (Esecuzione a vuoto per 50 cicli)...")
        for (i in 1..50) {
            val a = (plainTextLocationA * plainTextLocationB).mod(P)
            PaillierEncryption.encrypt(plainTextLocationA, pubKey)
        }
    }

    // ==========================================
    // LIVELLO 0: Baseline (Nessuna Protezione)
    // ==========================================
    private fun testLevel0Plaintext(): BenchmarkResult {
        var totalTimeNs = 0L
        var payloadSize = 0

        for (i in 1..ITERATIONS) {
            val time = measureNanoTime {
                // Calcolo in chiaro
                val diff = (plainTextLocationA - plainTextLocationB).abs()
                val isMatch = diff < BigInteger.valueOf(500)
            }
            totalTimeNs += time

            if(i == 1) {
                // Dimensione JSON di 2 coordinate in chiaro (es. "444900000")
                payloadSize = plainTextLocationA.toString().length * 2
            }
        }

        val avgTimeMs = (totalTimeNs.toDouble() / ITERATIONS) / 1_000_000.0
        return BenchmarkResult("L0_Plaintext", avgTimeMs, payloadSize)
    }

    // ==========================================
    // LIVELLO 1: Solo Blur (Location Perturbation)
    // ==========================================
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun testLevel1BlurOnly(): BenchmarkResult {
        var totalTimeNs = 0L
        var payloadSize = 0
        val blurR = BigInteger.valueOf(12345678)

        for (i in 1..ITERATIONS) {
            val time = measureNanoTime {
                // Offuscamento e calcolo su campo finito Zp
                val blurredA = (plainTextLocationA + blurR).mod(P)
                val blurredB = (plainTextLocationB + blurR).mod(P)
                val diff = (blurredA - blurredB).mod(P)
                // Nel paper la metrica è min(diff, P-diff)
                val dist = if (diff > P / BigInteger.TWO) P - diff else diff
            }
            totalTimeNs += time

            if(i == 1) {
                val blurredA = (plainTextLocationA + blurR).mod(P)
                payloadSize = blurredA.toString().length * 2
            }
        }

        val avgTimeMs = (totalTimeNs.toDouble() / ITERATIONS) / 1_000_000.0
        return BenchmarkResult("L1_Blur", avgTimeMs, payloadSize)
    }

    // ==========================================
    // LIVELLO 2: Solo Paillier (Distanza Cifrata)
    // ==========================================
    private fun testLevel2PaillierOnly(): BenchmarkResult {
        var totalTimeNs = 0L
        var payloadSize = 0

        for (i in 1..ITERATIONS) {
            val time = measureNanoTime {
                // Cifratura delle coordinate
                val encA = PaillierEncryption.encrypt(plainTextLocationA, pubKey)
                val encB = PaillierEncryption.encrypt(plainTextLocationB, pubKey)

                // Omomorfismo
                val encSum = (encA * encB).mod(nSquared)
                val decSum = PaillierEncryption.decrypt(encSum, pubKey, privKey)
            }
            totalTimeNs += time

            if(i == 1) {
                val encA = PaillierEncryption.encrypt(plainTextLocationA, pubKey)
                // Un ciphertext a 1024 bit convertito in stringa
                payloadSize = encA.toString().length * 2
            }
        }

        val avgTimeMs = (totalTimeNs.toDouble() / ITERATIONS) / 1_000_000.0
        return BenchmarkResult("L2_Paillier", avgTimeMs, payloadSize)
    }

    // ==========================================
    // LIVELLO 3: SamaritanCloud Completo
    // ==========================================
    private fun testLevel3FullSamaritanCloud(): BenchmarkResult {
        var totalTimeNs = 0L
        var payloadSize = 0

        for (i in 1..ITERATIONS) {
            val r = BigInteger.valueOf(9876543)
            val tol = BigInteger.valueOf(500)

            val time = measureNanoTime {
                // 1. Fase App Android: Blur e Cifratura Paillier
                val betaX = (plainTextLocationA + r).mod(P)
                val encR = PaillierEncryption.encrypt(r, pubKey)
                val encTol = PaillierEncryption.encrypt(tol, pubKey)

                // 2. Fase Server Python: Somma Omomorfica
                val targetEncR = PaillierEncryption.encrypt(BigInteger.valueOf(1111), pubKey)
                val t4SumEncrypted = (encR * targetEncR).mod(nSquared)

                // 3. Fase Distance Computation (Match)
                val decSum = PaillierEncryption.decrypt(t4SumEncrypted, pubKey, privKey)
            }
            totalTimeNs += time

            if(i == 1) {
                val betaX = (plainTextLocationA + r).mod(P)
                val encR = PaillierEncryption.encrypt(r, pubKey)
                val encTol = PaillierEncryption.encrypt(tol, pubKey)
                // Tupla Completa: BetaX, BetaY (in chiaro Zp) + EncR, EncTol, PubKey (Cifrati)
                payloadSize = (betaX.toString().length * 2) + (encR.toString().length * 2) + pubKey.toString().length
            }
        }

        val avgTimeMs = (totalTimeNs.toDouble() / ITERATIONS) / 1_000_000.0
        return BenchmarkResult("L3_Full_SamaritanCloud", avgTimeMs, payloadSize)
    }
}
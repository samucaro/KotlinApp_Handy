package com.unibo.handy.benchmark

import android.util.Log
import com.unibo.handy.domain.PaillierEncryption
import java.math.BigInteger
import kotlin.system.measureTimeMillis

// Data class per raccogliere i risultati da esportare su Excel
data class BenchmarkResult(
    val levelName: String,
    val avgExecutionTimeMs: Double,
    val estimatedPayloadSizeBytes: Int
)

object AblationStudyBenchmark {

    private const val TAG = "SamaritanBenchmark"
    private const val ITERATIONS = 100 // Numero di test per fare la media statistica

    // Dati fittizi per il test (es. coordinate GPS convertite in interi/BigInteger)
    private val plainTextLocationA = BigInteger.valueOf(4189025) // Es. Latitudine Roma
    private val plainTextLocationB = BigInteger.valueOf(4189100)

    // Setup Paillier
    private val keys = PaillierEncryption.keygen()
    private val pubKey = keys.first
    private val privKey = keys.second
    private val P = BigInteger.probablePrime(256, PaillierEncryption.RANDOM_GENERATOR) // Modulo per il Blur

    fun runFullStudy(): List<BenchmarkResult> {
        Log.i(TAG, "--- INIZIO ABLATION STUDY ---")
        warmUpJvm() // Riscalda il processore

        val results = mutableListOf<BenchmarkResult>()

        results.add(testLevel0Plaintext())
        results.add(testLevel1BlurOnly())
        results.add(testLevel2PaillierOnly())
        results.add(testLevel3FullSamaritanCloud())

        Log.i(TAG, "--- FINE ABLATION STUDY ---")
        return results
    }

    private fun warmUpJvm() {
        Log.d(TAG, "JVM Warm-up in corso...")
        for (i in 1..50) {
            val a = plainTextLocationA * plainTextLocationB
            PaillierEncryption.encrypt(plainTextLocationA, pubKey)
        }
    }

    // ==========================================
    // LIVELO 0: Nessuna Protezione (Baseline)
    // ==========================================
    private fun testLevel0Plaintext(): BenchmarkResult {
        var totalTime = 0L
        var payloadSize = 0

        for (i in 1..ITERATIONS) {
            val time = measureTimeMillis {
                // Calcolo della distanza (es. differenza quadratica)
                val diff = plainTextLocationA - plainTextLocationB
                val distanceSquared = diff * diff
            }
            totalTime += time

            if(i == 1) {
                // Stimiamo i byte necessari per trasmettere il dato in chiaro (es. 2 Int = 8 byte)
                payloadSize = plainTextLocationA.toByteArray().size * 2
            }
        }

        val avgTime = totalTime.toDouble() / ITERATIONS
        Log.d(TAG, "Lvl 0 (Plaintext): Avg Time = $avgTime ms | Payload = $payloadSize bytes")
        return BenchmarkResult("L0_Plaintext", avgTime, payloadSize)
    }

    // ==========================================
    // LIVELO 1: Solo Blur (Location Perturbation)
    // ==========================================
    private fun testLevel1BlurOnly(): BenchmarkResult {
        var totalTime = 0L
        var payloadSize = 0

        for (i in 1..ITERATIONS) {
            val blurR = BigInteger.valueOf(12345) // Blur personalizzato fittizio
            val time = measureTimeMillis {
                // p_ji - r_i mod P
                val blurredA = (plainTextLocationA - blurR).mod(P)
                val blurredB = (plainTextLocationB - blurR).mod(P)
                val diff = blurredA - blurredB
                val distanceSquared = diff * diff
            }
            totalTime += time

            if(i == 1) {
                payloadSize = (plainTextLocationA - blurR).mod(P).toByteArray().size * 2
            }
        }

        val avgTime = totalTime.toDouble() / ITERATIONS
        Log.d(TAG, "Lvl 1 (Blur Only): Avg Time = $avgTime ms | Payload = $payloadSize bytes")
        return BenchmarkResult("L1_Blur", avgTime, payloadSize)
    }

    // ==========================================
    // LIVELO 2: Solo Paillier (Crittografia)
    // ==========================================
    private fun testLevel2PaillierOnly(): BenchmarkResult {
        var totalTime = 0L
        var payloadSize = 0

        for (i in 1..ITERATIONS) {
            val time = measureTimeMillis {
                val encA = PaillierEncryption.encrypt(plainTextLocationA, pubKey)
                val encB = PaillierEncryption.encrypt(plainTextLocationB, pubKey)

                // Addizione omomorfica: E(A) * E(B) mod n^2
                val nSquared = pubKey * pubKey
                val encSum = (encA * encB).mod(nSquared)

                // Decrittazione da parte del worker
                val decSum = PaillierEncryption.decrypt(encSum, pubKey, privKey)
            }
            totalTime += time

            if(i == 1) {
                val encA = PaillierEncryption.encrypt(plainTextLocationA, pubKey)
                payloadSize = encA.toByteArray().size * 2 // Cifrati pesanti!
            }
        }

        val avgTime = totalTime.toDouble() / ITERATIONS
        Log.d(TAG, "Lvl 2 (Paillier Only): Avg Time = $avgTime ms | Payload = $payloadSize bytes")
        return BenchmarkResult("L2_Paillier", avgTime, payloadSize)
    }

    // ==========================================
    // LIVELO 3: SamaritanCloud Completo
    // ==========================================
    private fun testLevel3FullSamaritanCloud(): BenchmarkResult {
        // Qui unirà la logica del Livello 1 (Blurring) e del Livello 2 (Paillier)
        // Calcolando esattamente la formula [7] del paper con i vari T_j
        var totalTime = 0L
        var payloadSize = 0

        for (i in 1..ITERATIONS) {
            val time = measureTimeMillis {
                // SIMULAZIONE COMPLETA DELLA REDISTRIBUZIONE E CALCOLO DISTANZA
                // ... il suo codice esatto che mescola blur e Paillier ...
            }
            totalTime += time

            if(i == 1) {
                // La dimensione di una intera 6-tuple del paper SamaritanCloud
                payloadSize = pubKey.toByteArray().size * 6
            }
        }

        val avgTime = totalTime.toDouble() / ITERATIONS
        Log.d(TAG, "Lvl 3 (Full Samaritan): Avg Time = $avgTime ms | Payload = $payloadSize bytes")
        return BenchmarkResult("L3_Full", avgTime, payloadSize)
    }
}
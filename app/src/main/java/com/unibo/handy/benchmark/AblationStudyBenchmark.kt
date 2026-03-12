package com.unibo.handy.benchmark

import android.util.Log
import com.unibo.handy.domain.crypto.PaillierEncryption
import java.math.BigInteger
import java.security.SecureRandom
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.system.measureNanoTime

data class BenchmarkResult(
    val phase: String,
    val levelName: String,
    val keyBits: Int,
    val avgExecutionTimeMs: Double,
    val stdDevMs: Double,
    val estimatedPayloadSizeBytes: Int
)

object AblationStudyBenchmark {

    private const val TAG = "AblationStudy"
    private const val ITERATIONS = 100

    private const val P_LONG = 999999937L
    private const val X_H = 444900000L
    private const val Y_H = 113400000L
    private const val X_R = 444900100L
    private const val Y_R = 113400100L
    private const val TOLERANCE = 500L

    private const val R_C = 812345678L
    private const val R_S = 698765432L
    private const val C_RS = 111222333L
    private const val C_HS = 444555666L
    private const val R_G = 999888777L

    fun runFullStudy() {
        Log.e(TAG, "=========================================================")
        Log.e(TAG, "INIZIO ABLATION STUDY: HEARTBEAT, REQUEST, MATCH")
        Log.e(TAG, "=========================================================")

        val results = mutableListOf<BenchmarkResult>()

        listOf(1024, 2048).forEach { keySize ->
            Log.e(TAG, "Generazione chiavi a $keySize-bit in corso...")
            val (pubKey, privKey) = PaillierEncryption.keygen(keySize)

            warmUpJvm(pubKey)

            results.addAll(runHeartbeatAblation(keySize, pubKey))
            results.addAll(runHelpRequestAblation(keySize, pubKey))
            results.addAll(runMatchAblation(keySize, pubKey, privKey))
        }

        Log.e(TAG, "=========================================================")
        Log.e(TAG, "Fase, Livello, KeySize, TempoMedio(ms), DeviazioneStandard(+/- ms), Payload(Bytes)")
        results.forEach {
            val mean = String.format(Locale.US, "%.4f", it.avgExecutionTimeMs)
            val std = String.format(Locale.US, "%.4f", it.stdDevMs)
            Log.e(TAG, "${it.phase}, ${it.levelName}, ${it.keyBits}, $mean, $std, ${it.estimatedPayloadSizeBytes}")
        }
        Log.e(TAG, "=========================================================")
    }

    // ==========================================
    // MOTORE STATISTICO (Media e Deviazione Standard)
    // ==========================================
    private inline fun measureAblation(iterations: Int, crossinline block: () -> Unit): Pair<Double, Double> {
        val timesMs = DoubleArray(iterations)
        for (i in 0 until iterations) {
            timesMs[i] = measureNanoTime { block() } / 1e6
        }
        val mean = timesMs.average()
        val variance = timesMs.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)
        return Pair(mean, stdDev)
    }

    // ==========================================
    // FASE 1: HEARTBEAT (Helper)
    // ==========================================
    private fun runHeartbeatAblation(keyBits: Int, pubKey: BigInteger): List<BenchmarkResult> {
        val phase = "Heartbeat"
        val res = mutableListOf<BenchmarkResult>()
        var dummy = 0L

        // L0: Plaintext
        val (meanL0, stdL0) = measureAblation(ITERATIONS) {
            val x = X_H
            val y = Y_H
            dummy += (x+y)
        }
        val sizeL0 = Long.SIZE_BYTES * 2
        res.add(BenchmarkResult(phase, "L0_Plaintext", keyBits, meanL0, stdL0, sizeL0))

        // L1: Solo Blur
        val (meanL1, stdL1) = measureAblation(ITERATIONS) {
            val bx = modSub(X_H, R_C)
            val by = modSub(Y_H, R_C)
            dummy += (bx+by)
        }
        val sizeL1 = Long.SIZE_BYTES * 2
        res.add(BenchmarkResult(phase, "L1_Blur", keyBits, meanL1, stdL1, sizeL1))

        val (meanL2, stdL2) = measureAblation(ITERATIONS) {
            val ex = PaillierEncryption.encrypt(BigInteger.valueOf(X_H), pubKey)
            val ey = PaillierEncryption.encrypt(BigInteger.valueOf(Y_H), pubKey)
            dummy += (ex.toLong() + ey.toLong())
        }
        val sizeL2 = (PaillierEncryption.encrypt(BigInteger.valueOf(X_H), pubKey).toByteArray().size * 2)
        res.add(BenchmarkResult(phase, "L2_Paillier", keyBits, meanL2, stdL2, sizeL2))

        // L3: Full Protocol
        val (meanL3, stdL3) = measureAblation(ITERATIONS) {
            val bx = modSub(X_H, R_C)
            val by = modSub(Y_H, R_C)
            val encNoise = PaillierEncryption.encrypt(BigInteger.valueOf(R_C), pubKey)
            dummy += (bx+by+encNoise.toLong())
        }
        val sizeL3 = (Long.SIZE_BYTES * 2) + PaillierEncryption.encrypt(BigInteger.valueOf(R_C), pubKey).toByteArray().size
        res.add(BenchmarkResult(phase, "L3_Full_Protocol", keyBits, meanL3, stdL3, sizeL3))

        return res
    }

    // ==========================================
    // FASE 2: HELP-REQUEST (Requester)
    // ==========================================
    private fun runHelpRequestAblation(keyBits: Int, pubKey: BigInteger): List<BenchmarkResult> {
        val phase = "HelpRequest"
        val res = mutableListOf<BenchmarkResult>()
        var dummy = 0L

        // L0: Plaintext
        val (meanL0, stdL0) = measureAblation(ITERATIONS) {
            val x = X_R
            val y = Y_R
            val t = TOLERANCE
            dummy += (x+y+t)
        }
        res.add(BenchmarkResult(phase, "L0_Plaintext", keyBits, meanL0, stdL0, 24))

        // L1: Blur
        val (meanL1, stdL1) = measureAblation(ITERATIONS) {
            val bx = modAdd(X_R, R_C)
            val by = modAdd(Y_R, R_C)
            val bt = modAdd(TOLERANCE, R_C)
            dummy += (bx+by+bt)
        }
        res.add(BenchmarkResult(phase, "L1_Blur", keyBits, meanL1, stdL1, 24))

        val (meanL2, stdL2) = measureAblation(ITERATIONS) {
            val ex = PaillierEncryption.encrypt(BigInteger.valueOf(X_R), pubKey)
            val ey = PaillierEncryption.encrypt(BigInteger.valueOf(Y_R), pubKey)
            val et = PaillierEncryption.encrypt(BigInteger.valueOf(TOLERANCE), pubKey)
            dummy += (ex.toLong() + ey.toLong() + et.toLong())
        }
        val sizeL2 = (PaillierEncryption.encrypt(BigInteger.valueOf(X_R), pubKey).toByteArray().size * 3)
        res.add(BenchmarkResult(phase, "L2_Paillier", keyBits, meanL2, stdL2, sizeL2))

        // L3: Full Protocol
        val (meanL3, stdL3) = measureAblation(ITERATIONS) {
            val bx = modAdd(X_R, R_C)
            val by = modAdd(Y_R, R_C)
            val encNoise = PaillierEncryption.encrypt(BigInteger.valueOf(R_C), pubKey)
            val encTol = PaillierEncryption.encrypt(BigInteger.valueOf(TOLERANCE), pubKey)
            dummy += (bx+by+encNoise.toLong()+encTol.toLong())
        }
        val sizeL3 = 16 + (PaillierEncryption.encrypt(BigInteger.valueOf(X_R), pubKey).toByteArray().size * 2)
        res.add(BenchmarkResult(phase, "L3_Full_Protocol", keyBits, meanL3, stdL3, sizeL3))

        return res
    }

    // ==========================================
    // FASE 3: MATCH (Distanza Euclidea)
    // ==========================================
    private fun runMatchAblation(keyBits: Int, pubKey: BigInteger, privKey: PaillierEncryption.PrivateKey): List<BenchmarkResult> {
        val phase = "Match"
        val res = mutableListOf<BenchmarkResult>()
        var dummy = 0L

        val t3X = modAdd(X_R, R_C) + C_RS + R_G
        val t3Y = modAdd(Y_R, R_C) + C_RS + R_G
        val prblurX = modSub(X_H, R_S) - C_HS + R_G
        val prblurY = modSub(Y_H, R_S) - C_HS + R_G
        val t4Enc = PaillierEncryption.encrypt(BigInteger.valueOf(R_C + R_S), pubKey)
        val t6Enc = PaillierEncryption.encrypt(BigInteger.valueOf(TOLERANCE), pubKey)
        val t5 = C_RS - C_HS
        // Per layer 2
        val originalDx = abs(X_H - X_R)
        val originalDy = abs(Y_H - Y_R)
        val encDx = PaillierEncryption.encrypt(BigInteger.valueOf(originalDx), pubKey)
        val encDy = PaillierEncryption.encrypt(BigInteger.valueOf(originalDy), pubKey)

        // L0: Plaintext
        val (meanL0, stdL0) = measureAblation(ITERATIONS) {
            val dx = abs(X_H - X_R).toDouble()
            val dy = abs(Y_H - Y_R).toDouble()
            val dist = sqrt((dx * dx) + (dy * dy))
            val isMatch = dist <= TOLERANCE
            dummy += if (isMatch) 1L else 0L
        }
        res.add(BenchmarkResult(phase, "L0_Plaintext", keyBits, meanL0, stdL0, 0))

        // L1: Decifratura Blur Matematico
        val (meanL1, stdL1) = measureAblation(ITERATIONS) {
            val noiseSum = (R_C + R_S) + (C_RS - C_HS)
            val cleanDx = minMetricDistance(modSub(t3X, modAdd(prblurX, noiseSum))).toDouble()
            val cleanDy = minMetricDistance(modSub(t3Y, modAdd(prblurY, noiseSum))).toDouble()
            val dist = sqrt((cleanDx * cleanDx) + (cleanDy * cleanDy))
            val isMatch = dist <= TOLERANCE
            dummy += if (isMatch) 1L else 0L
        }
        res.add(BenchmarkResult(phase, "L1_Blur", keyBits, meanL1, stdL1, 0))

        // L2: Approccio Naïve (Paillier puro sulle coordinate)
        val (meanL2, stdL2) = measureAblation(ITERATIONS) {
            // Il client è costretto a decifrare asimmetricamente 3 variabili pesanti!
            val decDx = PaillierEncryption.decrypt(encDx, pubKey, privKey).toLong().toDouble()
            val decDy = PaillierEncryption.decrypt(encDy, pubKey, privKey).toLong().toDouble()
            val decT = PaillierEncryption.decrypt(t6Enc, pubKey, privKey).toLong()

            // Calcolo della distanza coerente con l'equazione
            val dist = sqrt((decDx * decDx) + (decDy * decDy))
            val isMatch = dist <= decT
            dummy += if (isMatch) 1L else 0L
        }
        res.add(BenchmarkResult(phase, "L2_Paillier", keyBits, meanL2, stdL2, 0))

        // L2: Full Protocol SamaritanCloud
        val (meanL3, stdL3) = measureAblation(ITERATIONS) {
            val t4Dec = PaillierEncryption.decrypt(t4Enc, pubKey, privKey).toLong()
            val t6Dec = PaillierEncryption.decrypt(t6Enc, pubKey, privKey).toLong()
            val totalNoise = modAdd(t4Dec, t5)
            val valX = modAdd(prblurX, totalNoise)
            val dx = minMetricDistance(modSub(t3X, valX)).toDouble()
            val valY = modAdd(prblurY, totalNoise)
            val dy = minMetricDistance(modSub(t3Y, valY)).toDouble()
            val dist = sqrt((dx * dx) + (dy * dy))
            val isMatch = dist <= t6Dec
            dummy += if (isMatch) 1L else 0L
        }
        res.add(BenchmarkResult(phase, "L3_Full_Protocol", keyBits, meanL3, stdL3, 0))

        return res
    }

    private fun modAdd(a: Long, b: Long): Long = ((a % P_LONG) + (b % P_LONG)) % P_LONG
    private fun modSub(a: Long, b: Long): Long {
        val res = (a % P_LONG) - (b % P_LONG)
        return if (res < 0) res + P_LONG else res
    }
    private fun minMetricDistance(delta: Long): Long = if (delta > P_LONG / 2) P_LONG - delta else delta
    private fun warmUpJvm(pubKey: BigInteger) {
        for (i in 1..10) PaillierEncryption.encrypt(BigInteger.valueOf(X_H), pubKey)
    }
}
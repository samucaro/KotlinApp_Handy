package com.unibo.handy.domain.crypto

import java.math.BigInteger
import java.security.SecureRandom

/**
 * Motore per la crittografia omomorfica additiva (Sistema di Paillier).
 * Permette operazioni matematiche su testi cifrati: E(a+b) = E(a)*E(b) mod n^2
 */
object PaillierEncryption {
    const val KEY_SIZE = 2048
    val RANDOM_GENERATOR = SecureRandom()

    // Struttura per contenere la chiave privata
    data class PrivateKey(val phiN: BigInteger, val d: BigInteger)

    /**
     * Cifra un intero 'm' usando il modulo pubblico 'n'.
     * Formula: c = (1 + mN) * r^N mod N^2
     */
    fun encrypt(m: BigInteger, n: BigInteger): BigInteger {
        val r = BigInteger(KEY_SIZE, RANDOM_GENERATOR)
        val nSquared = n * n

        // (1 + mN)
        val part1 = (BigInteger.ONE + m.multiply(n))
        // r^N mod N^2
        val part2 = r.modPow(n, nSquared)

        return (part1 * part2).mod(nSquared)
    }

    /**
     * Decifra un ciphertext 'c' usando la chiave privata (lambda/mu) o la variante semplificata.
     */
    fun decrypt(c: BigInteger, n: BigInteger, privateKey: PrivateKey): BigInteger {
        val nSquared = n * n

        // Primo passo: C1 = c^phi(N) mod N^2
        val step1 = c.modPow(privateKey.phiN, nSquared)

        // Secondo passo: C = C1^d mod N^2 = (1 + mN) mod N^2
        val step2 = step1.modPow(privateKey.d, nSquared)

        // Terzo passo: m = (C - 1) / N
        val m = (step2 - BigInteger.ONE).divide(n)

        return m
    }

    /**
     * Test di Primalità di Fermat.
     */
    fun fermatPrimalityTest(m: BigInteger): Boolean {
        val two = BigInteger.valueOf(2)
        return two.modPow(m - BigInteger.ONE, m) == BigInteger.ONE
    }

    /**
     * NOTA ARCHITETTURALE PER LA TESI:
     * Questa funzione non viene invocata direttamente dal client Android in produzione.
     * In un'architettura SamaritanCloud reale, questa funzione appartiene al
     * Trusted Third Party (TTP) che genera la coppia di chiavi condivise per il gruppo.
     * Viene mantenuta qui a scopo di validazione matematica e per eseguire l'Ablation Test.
     *
     * @return Coppia: Public Key (n) e Private Key.
     */
    fun keygen(keyBits: Int = KEY_SIZE): Pair<BigInteger, PrivateKey> {

        var p = BigInteger(keyBits/2, RANDOM_GENERATOR)
        while (!fermatPrimalityTest(p))
            p = p.nextProbablePrime()

        var q = BigInteger(keyBits/2, RANDOM_GENERATOR)
        while (!fermatPrimalityTest(q))
            q = q.nextProbablePrime()

        val n = p * q

        val pMinusOne = p - BigInteger.ONE
        val qMinusOne = q - BigInteger.ONE
        val phiN = pMinusOne * qMinusOne

        val d = phiN.modInverse(n)

        val privateKey = PrivateKey(phiN, d)

        return Pair(n, privateKey)
    }
}
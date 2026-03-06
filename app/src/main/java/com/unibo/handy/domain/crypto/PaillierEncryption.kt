package com.unibo.handy.domain.crypto

import java.math.BigInteger
import java.security.SecureRandom

/**
 * Motore per la crittografia omomorfica additiva (Sistema di Paillier).
 * Permette operazioni matematiche su testi cifrati: E(a+b) = E(a)*E(b) mod n^2
 */
object PaillierEncryption {
    const val KEY_SIZE = 1024
    val RANDOM_GENERATOR = SecureRandom()

    /**
     * Cifra un intero 'm' usando il modulo pubblico 'n'.
     * Formula: c = (1 + n)^m * r^n mod n^2
     */
    fun encrypt(m: BigInteger, n: BigInteger): BigInteger {
        val r = BigInteger(KEY_SIZE, RANDOM_GENERATOR)
        return (BigInteger.ONE + n).modPow(m, n*n) * r.modPow(n, n*n)
    }

    /**
     * Decifra un ciphertext 'c' usando la chiave privata (lambda/mu) o la variante semplificata.
     */
    fun decrypt(c: BigInteger, n: BigInteger, privateKey: BigInteger): BigInteger {

        val r = c.modPow(privateKey, n)

        return ((c*r.modPow(-n, n*n) - BigInteger.ONE)/n).mod(n)
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
    fun keygen(): Pair<BigInteger, BigInteger> {

        var p = BigInteger(KEY_SIZE, RANDOM_GENERATOR)
        while (!fermatPrimalityTest(p))
            p = p.nextProbablePrime()

        var q = BigInteger(KEY_SIZE, RANDOM_GENERATOR)
        while (!fermatPrimalityTest(q))
            q = q.nextProbablePrime()

        val n = p * q
        val phiN = (p - BigInteger.ONE) * (q - BigInteger.ONE)
        val privateKey = n.modInverse(phiN)

        return Pair(n, privateKey)
    }
}
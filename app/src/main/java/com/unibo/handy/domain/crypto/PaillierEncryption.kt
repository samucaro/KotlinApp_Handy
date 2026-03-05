package com.unibo.handy.domain.crypto

import java.math.BigInteger
import java.security.SecureRandom

/**
 * NOTA ARCHITETTURALE:
 * La funzione keygen() non viene invocata direttamente dal client Android.
 * In un'architettura di produzione SamaritanCloud, questa funzione
 * appartiene al Trusted Third Party (TTP) che genera la coppia di chiavi
 * condivise per tutto il gruppo di utenti.
 */

object PaillierEncryption {
    const val KEY_SIZE = 1024
    val RANDOM_GENERATOR = SecureRandom()

    fun encrypt(m: BigInteger, n: BigInteger): BigInteger {
        val r = BigInteger(KEY_SIZE, RANDOM_GENERATOR)
        return (BigInteger.ONE + n).modPow(m, n*n) * r.modPow(n, n*n)
    }

    fun decrypt(c: BigInteger, n: BigInteger, privateKey: BigInteger): BigInteger {

        val r = c.modPow(privateKey, n)

        return ((c*r.modPow(-n, n*n) - BigInteger.ONE)/n).mod(n)
    }

    fun fermatPrimalityTest(m: BigInteger): Boolean {
        val two = BigInteger.valueOf(2)
        return two.modPow(m - BigInteger.ONE, m) == BigInteger.ONE
    }

    /**
     * @return Coppia di Public Key (n) e Private Key (n.modInverse(phi_n)
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
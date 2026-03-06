package com.unibo.handy.domain.crypto

/**
 * Interfaccia per l'astrazione delle operazioni crittografiche simmetriche locali (AES).
 * Utilizzata dal SecureKeyRepository per cifrare la chiave privata Paillier prima
 * di salvarla su disco (DataStore/SharedPreferences).
 */
interface ICryptoManager {
    fun encrypt(bytes: ByteArray): ByteArray
    fun decrypt(bytes: ByteArray): ByteArray
}
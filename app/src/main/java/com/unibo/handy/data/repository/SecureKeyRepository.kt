package com.unibo.handy.data.repository

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.unibo.handy.domain.CryptoManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.math.BigInteger
import javax.inject.Inject

// Estensione per inizializzare il DataStore
private val Context.dataStore by preferencesDataStore(name = "secure_keys")
class SecureKeyRepository @Inject constructor(
    private val context: Context,
    private val cryptoManager: CryptoManager
) {
    companion object {
        val PRIVATE_KEY = stringPreferencesKey("group_private_key")
        val PUBLIC_MODULUS = stringPreferencesKey("public_modulus")
    }

    // Salva le chiavi criptandole e codificandole in Base64
    suspend fun saveKeys(privateKey: BigInteger, modulus: BigInteger) {
        val encryptedPrivKey = cryptoManager.encrypt(privateKey.toByteArray())
        val encryptedModulus = cryptoManager.encrypt(modulus.toByteArray())

        context.dataStore.edit { prefs ->
            prefs[PRIVATE_KEY] = Base64.encodeToString(encryptedPrivKey, Base64.DEFAULT)
            prefs[PUBLIC_MODULUS] = Base64.encodeToString(encryptedModulus, Base64.DEFAULT)
        }
    }

    // Recupera la chiave privata
    suspend fun getPrivateKey(): BigInteger? {
        val encryptedBase64 = context.dataStore.data.map { it[PRIVATE_KEY] }.first() ?: return null
        val encryptedBytes = Base64.decode(encryptedBase64, Base64.DEFAULT)
        val decryptedBytes = cryptoManager.decrypt(encryptedBytes)
        return BigInteger(decryptedBytes)
    }

    suspend fun getPublicModulus(): BigInteger? {
        val encryptedBase64 = context.dataStore.data.map { it[PUBLIC_MODULUS] }.first() ?: return null
        val encryptedBytes = Base64.decode(encryptedBase64, Base64.DEFAULT)
        val decryptedBytes = cryptoManager.decrypt(encryptedBytes)
        return BigInteger(decryptedBytes)
    }
}
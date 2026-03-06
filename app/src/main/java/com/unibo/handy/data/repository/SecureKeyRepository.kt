package com.unibo.handy.data.repository

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.unibo.handy.domain.crypto.ICryptoManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

// Estensione delegata per inizializzare il DataStore (moderna alternativa asincrona alle SharedPreferences)
private val Context.dataStore by preferencesDataStore(name = "secure_keys")

/**
 * Repository dedicato alla persistenza sicura delle chiavi crittografiche (Encryption at Rest).
 * Utilizza il DataStore di Jetpack combinato con l'Hardware Keystore di Android (ICryptoManager)
 * per garantire che le chiavi asimmetriche di Paillier non siano mai esposte in chiaro sul disco.
 */
@Singleton
class SecureKeyRepository @Inject constructor(
    private val context: Context,
    private val cryptoManager: ICryptoManager
) {
    companion object {
        val PRIVATE_KEY = stringPreferencesKey("group_private_key")
        val PUBLIC_MODULUS = stringPreferencesKey("public_modulus")
    }

    /**
     * Simula il Trusted Third Party (TTP) o Key Generation Center.
     * Nell'architettura reale SamaritanCloud, queste chiavi verrebbero scaricate in modo sicuro
     * dal server tramite TLS durante la fase di registrazione. Qui vengono iniettate localmente al primo avvio.
     */
    suspend fun initKeysIfEmpty() {
        val existingModulus = getPublicModulus()
        if (existingModulus == null) {
            // ATTENZIONE (Ablation Test):
            // Questi sono valori "giocattolo"
            // Devono essere sostituiti con stringhe di chiavi reali a 1024 o 2048 bit generate
            // tramite PaillierEncryption.keygen() prima di raccogliere i dati prestazionali!
            val mockPublicModulus = BigInteger("3233")
            val mockPrivateKey = BigInteger("2753")

            saveKeys(mockPrivateKey, mockPublicModulus)
        }
    }

    /**
     * Cifra la chiave privata e il modulo pubblico a livello di byte usando AES (tramite Keystore)
     * e li salva nel DataStore codificati in Base64 per compatibilità di formato.
     */
    suspend fun saveKeys(privateKey: BigInteger, modulus: BigInteger) {
        val encryptedPrivKey = cryptoManager.encrypt(privateKey.toByteArray())
        val encryptedModulus = cryptoManager.encrypt(modulus.toByteArray())

        context.dataStore.edit { prefs ->
            prefs[PRIVATE_KEY] = Base64.encodeToString(encryptedPrivKey, Base64.DEFAULT)
            prefs[PUBLIC_MODULUS] = Base64.encodeToString(encryptedModulus, Base64.DEFAULT)
        }
    }

    /**
     * Recupera la chiave privata dal DataStore, la decodifica dal formato Base64
     * e la decifra in modo trasparente tramite l'Hardware Keystore.
     */
    suspend fun getPrivateKey(): BigInteger? {
        val encryptedBase64 = context.dataStore.data.map { it[PRIVATE_KEY] }.first() ?: return null
        val encryptedBytes = Base64.decode(encryptedBase64, Base64.DEFAULT)
        val decryptedBytes = cryptoManager.decrypt(encryptedBytes)
        return BigInteger(decryptedBytes)
    }

    /**
     * Recupera il modulo pubblico di gruppo dal DataStore.
     */
    suspend fun getPublicModulus(): BigInteger? {
        val encryptedBase64 = context.dataStore.data.map { it[PUBLIC_MODULUS] }.first() ?: return null
        val encryptedBytes = Base64.decode(encryptedBase64, Base64.DEFAULT)
        val decryptedBytes = cryptoManager.decrypt(encryptedBytes)
        return BigInteger(decryptedBytes)
    }
}
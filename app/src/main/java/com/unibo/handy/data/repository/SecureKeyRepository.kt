package com.unibo.handy.data.repository

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.unibo.handy.domain.crypto.ICryptoManager
import com.unibo.handy.domain.crypto.PaillierEncryption
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
        val PRIVATE_KEY_PHI_N = stringPreferencesKey("private_key_phi_n")
        val PRIVATE_KEY_D = stringPreferencesKey("private_key_d")
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
            val keyPair = PaillierEncryption.keygen()
            val publicModulus = keyPair.first
            Log.e("CHIAVI_SERVER", "Copia questo valore in Python: \n$publicModulus")
            val privateKey = keyPair.second

            saveKeys(privateKey, publicModulus)
        }
    }

    /**
     * Cifra la chiave privata e il modulo pubblico a livello di byte usando AES (tramite Keystore)
     * e li salva nel DataStore codificati in Base64 per compatibilità di formato.
     */
    suspend fun saveKeys(privateKey: PaillierEncryption.PrivateKey, modulus: BigInteger) {
        val encryptedPhiN = cryptoManager.encrypt(privateKey.phiN.toByteArray())
        val encryptedD = cryptoManager.encrypt(privateKey.d.toByteArray())
        val encryptedModulus = cryptoManager.encrypt(modulus.toByteArray())

        context.dataStore.edit { prefs ->
            prefs[PRIVATE_KEY_PHI_N] = Base64.encodeToString(encryptedPhiN, Base64.DEFAULT)
            prefs[PRIVATE_KEY_D] = Base64.encodeToString(encryptedD, Base64.DEFAULT)
            prefs[PUBLIC_MODULUS] = Base64.encodeToString(encryptedModulus, Base64.DEFAULT)
        }
    }

    /**
     * Recupera la chiave privata dal DataStore, la decodifica dal formato Base64
     * e la decifra in modo trasparente tramite l'Hardware Keystore.
     */
    suspend fun getPrivateKey(): PaillierEncryption.PrivateKey? {
        val prefs = context.dataStore.data.first()

        // Recupera le due stringhe Base64
        val phiNBase64 = prefs[PRIVATE_KEY_PHI_N] ?: return null
        val dBase64 = prefs[PRIVATE_KEY_D] ?: return null

        // Decodifica da Base64
        val phiNBytes = Base64.decode(phiNBase64, Base64.DEFAULT)
        val dBytes = Base64.decode(dBase64, Base64.DEFAULT)

        // Decifra tramite l'Hardware Keystore
        val decryptedPhiN = cryptoManager.decrypt(phiNBytes)
        val decryptedD = cryptoManager.decrypt(dBytes)

        // Ricostruisce l'oggetto PrivateKey richiesto dal PrivacyEngine
        return PaillierEncryption.PrivateKey(BigInteger(decryptedPhiN), BigInteger(decryptedD))
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
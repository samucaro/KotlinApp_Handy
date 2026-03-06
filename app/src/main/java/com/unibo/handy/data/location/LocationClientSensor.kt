package com.unibo.handy.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Hardware Abstraction Layer (HAL) per il tracciamento geografico.
 * Incapsula la logica di comunicazione con il FusedLocationProviderClient di Google Play Services.
 */
class LocationClientSensor @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    // Client ottimizzato che combina GPS, Wi-Fi e reti cellulari per bilanciare precisione e batteria
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    /**
     * Recupera la posizione fisica esatta dell'utente sul globo terrestre.
     * @return L'oggetto Location contenente latitudine e longitudine, o null in caso di fallimento.
     */
    @SuppressLint("MissingPermission") // I permessi vengono già garantiti e controllati in MainActivity
    suspend fun getCurrentLocation(): Location? {
        return try {
            // Meccanismo di Fail-Safe: previene il blocco della Coroutine se l'hardware non risponde.
            // Timeout impostato a 15 secondi (15_000 ms).
            withTimeoutOrNull(15_000) {
                val request = CurrentLocationRequest.Builder()
                    // PRIORITY_HIGH_ACCURACY forza l'accensione del chip GPS satellitare
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .build()

                // Converte l'API basata su Callback (Task) di Google Play Services in una Suspend Function nativa
                fusedLocationClient.getCurrentLocation(request, null).await()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
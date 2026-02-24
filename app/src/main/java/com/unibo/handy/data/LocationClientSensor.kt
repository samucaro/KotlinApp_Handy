package com.unibo.handy.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.location.CurrentLocationRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/*
 * Classe intermediaria per ottenere la posizione dell'utente dai sensori dello smartphone tramite
 * FusedLocationProviderClient (Play Services)
 */
class LocationClientSensor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // qui sotto al cofano, sfruttando questa API (FusedLocationProviderClient), viene fatta una
    // chiamata alla HAL di Android per ottenere la posizione dell'utente
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? {
        return try {
            withTimeoutOrNull(15_000) {
                val request = CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .build()

                fusedLocationClient.getCurrentLocation(request, null).await()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
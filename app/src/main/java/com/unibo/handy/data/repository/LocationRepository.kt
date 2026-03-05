package com.unibo.handy.data.repository

import android.location.Location
import com.unibo.handy.data.location.LocationClientSensor
import com.unibo.handy.data.network.ServiceAPI
import com.unibo.handy.data.network.dto.HeartBeatDTO
import javax.inject.Inject

/**
 * Gestisce l'interazione con il sensore GPS e l'invio periodico della posizione (Heartbeat).
 * Implementa la Fase 2 del protocollo: Profile-Update-Request.
 */
class LocationRepository @Inject constructor(
    private val locationClient: LocationClientSensor,
    private val apiService: ServiceAPI
) {
    //Recupera la posizione attuale (Wrap del sensore)
    suspend fun getCurrentLocation(): Location? {
        return locationClient.getCurrentLocation()
    }

    suspend fun postHeartbeatToNetwork(
        userId: String,
        blurredX: Long,
        blurredY: Long,
        encryptedBlur: String
    ) {
        val dto = HeartBeatDTO(
            clientId = userId,
            blurredX = blurredX,
            blurredY = blurredY,
            encryptedBlur = encryptedBlur
        )

        val response = apiService.sendHeartbeat(dto)

        if (!response.isSuccessful) throw Exception("Server Error: ${response.code()}")
    }
}
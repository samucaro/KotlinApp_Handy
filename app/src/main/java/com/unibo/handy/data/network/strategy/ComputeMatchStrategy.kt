package com.unibo.handy.data.network.strategy

import android.util.Log
import com.google.gson.Gson
import com.unibo.handy.data.network.dto.TupleDTO
import com.unibo.handy.data.repository.MatchingRepository
import com.unibo.handy.domain.usecase.match.ComputeMatchUseCase
import com.unibo.handy.service.notifications.NotificationHelper
import javax.inject.Inject

class ComputeMatchStrategy @Inject constructor(
    private val computeMatchUseCase: ComputeMatchUseCase,
    private val matchingRepo: MatchingRepository,
    private val notificationHelper: NotificationHelper,
    private val gson: Gson
) : MessageStrategy {
    override suspend fun handle(payload: String) {
        try {
            val tuple = gson.fromJson(payload, TupleDTO::class.java)

            // Invocazione del Use Case per il calcolo del match
            val isCompatible = computeMatchUseCase(tuple)

            // Se il risultato è positivo, informa il server
            if (isCompatible) {
                matchingRepo.saveMatchToDb(tuple.t1RequesterId, tuple.t2TargetId)
                matchingRepo.notifyServerOfMatch(tuple.t1RequesterId, tuple.t2TargetId)

                // Lancia la notifica al sistema operativo
                notificationHelper.showMatchNotification()
            }
        } catch (e: Exception) {
            Log.e("Strategy", "Errore durante l'elaborazione del match: ${e.message}")
        }
    }
}
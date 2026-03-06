package com.unibo.handy.data.network.strategy

import android.util.Log
import com.google.gson.Gson
import com.unibo.handy.data.network.dto.TupleDTO
import com.unibo.handy.data.repository.MatchingRepository
import com.unibo.handy.domain.usecase.match.ComputeMatchUseCase
import com.unibo.handy.service.notifications.NotificationHelper
import javax.inject.Inject

/**
 * Strategia core del protocollo SamaritanCloud.
 * Invocata quando l'Helper riceve una Tupla crittografica da risolvere (Help-Request).
 */
class ComputeMatchStrategy @Inject constructor(
    private val computeMatchUseCase: ComputeMatchUseCase,
    private val matchingRepo: MatchingRepository,
    private val notificationHelper: NotificationHelper,
    private val gson: Gson
) : MessageStrategy {

    override suspend fun handle(payload: String) {
        try {
            // 1. Deserializzazione della tupla matematica
            val tuple = gson.fromJson(payload, TupleDTO::class.java)

            // 2. Invocazione del Livello di Dominio (PrivacyEngine via UseCase)
            val isCompatible = computeMatchUseCase(tuple)

            // 3. Risoluzione positiva: notifica server e utente
            if (isCompatible) {
                matchingRepo.saveMatchToDb(tuple.t1RequesterId, tuple.t2TargetId)
                matchingRepo.notifyServerOfMatch(tuple.t1RequesterId, tuple.t2TargetId)
                notificationHelper.showMatchNotification()
            }
        } catch (e: Exception) {
            Log.e("Strategy", "Errore durante l'elaborazione del match: ${e.message}")
        }
    }
}
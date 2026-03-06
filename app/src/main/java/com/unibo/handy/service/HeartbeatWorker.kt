package com.unibo.handy.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.unibo.handy.domain.usecase.profile.SendHeartbeatUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Worker delegato all'aggiornamento periodico della posizione offuscata (Heartbeat).
 * Garantisce l'esecuzione in "Deep Background" anche in condizioni di Doze Mode,
 * aderendo alle policy di risparmio energetico di Android.
 */
@HiltWorker
class HeartbeatWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sendHeartbeatUseCase: SendHeartbeatUseCase
) : CoroutineWorker(context, params) {

    /**
     * Esegue il task asincrono. Il sistema operativo garantisce un wakelock
     * (risveglio della CPU) per una durata massima di 10 minuti per completare questo blocco.
     */
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            // Delega la logica crittografica e di rete al livello di Dominio
            sendHeartbeatUseCase()
            Result.success()
        } catch (e: Exception) {
            Log.e("HeartbeatWorker", "Errore durante l'invio dell'heartbeat. Schedulo un retry.", e)
            // Istruisce il WorkManager ad applicare l'Exponential Backoff
            Result.retry()
        }
    }
}
package com.unibo.handy.data.network

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.unibo.handy.HandyApp
import com.unibo.handy.data.repository.LocationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class HeartbeatWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val locationRepo: LocationRepository
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i("HeartbeatWorker", "Periodic execution heartbeat started")

        return@withContext try {
            // sendHeartbeat() fa già il controllo su userDao.getUserSnapshot()
            // e verifica se l'utente è in helpModeActive.
            locationRepo.sendHeartbeat()
            Result.success()
        } catch (e: Exception) {
            Log.e("HeartbeatWorker", "Error sending heartbeat, retrying", e)
            // Result.retry() dice al WorkManager di riprovare più tardi con un backoff esponenziale
            Result.retry()
        }
    }
}
package com.unibo.handy.data.network

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.unibo.handy.HandyApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HeartbeatWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i("HeartbeatWorker", "Periodic execution heartbeat started")

        // Accede al repository tramite il singleton dell'Application
        val app = applicationContext as HandyApp
        val locationRepo = app.locationRepository

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
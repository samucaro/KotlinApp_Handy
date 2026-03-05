package com.unibo.handy.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.unibo.handy.data.network.MessageDispatcher
import com.unibo.handy.data.repository.UserRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HandyFcmService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Inject lateinit var dispatcher: MessageDispatcher
    @Inject lateinit var userRepository: UserRepository

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        if (remoteMessage.data.isNotEmpty()) {
            val action = remoteMessage.data["action"]
            val payload = remoteMessage.data["payload"]

            if (action != null && payload != null) {
                serviceScope.launch {
                    dispatcher.dispatch(action, payload)
                }
            } else {
                Log.w("HandyFCM", "Payload o Action mancante nel messaggio FCM")
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        // Invia questo token al server
        // In questo modo il server sa a chi instradare le Help-Request.
        serviceScope.launch {
            try {
                userRepository.updateFcmToken(token)
            } catch (e: Exception) {
                Log.e("HandyFCM", "Errore nell'aggiornamento del token", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
package com.unibo.handy.data.network

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.unibo.handy.HandyApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HandyFcmService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Verifichiamo che il messaggio contenga un payload di dati (Data Message)
        if (remoteMessage.data.isNotEmpty()) {
            val action = remoteMessage.data["action"]
            val payload = remoteMessage.data["payload"]

            Log.d("HandyFCM", "Ricevuto broadcast di matching. Action: $action")

            if (action != null && payload != null) {
                // Deleghiamo l'elaborazione al Dispatcher centralizzato
                val app = applicationContext as HandyApp
                serviceScope.launch {
                    app.realtimeDispatcher.dispatch(action, payload)
                }
            } else {
                Log.w("HandyFCM", "Payload o Action mancante nel messaggio FCM")
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i("HandyFCM", "Nuovo token FCM registrato: $token")

        // FONDAMENTALE: Inviare questo token al tuo server backend
        // In questo modo il server sa a chi instradare le Help-Request.
        val app = applicationContext as HandyApp
        serviceScope.launch {
            try {
                // Presuppone che tu aggiunga un metodo updateFcmToken nel UserRepository
                app.userRepository.updateFcmToken(token)
            } catch (e: Exception) {
                Log.e("HandyFCM", "Errore nell'aggiornamento del token", e)
            }
        }
    }
}
package com.unibo.handy

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class HandyFirebase : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Ricezione del broadcast dal server
        if (remoteMessage.data.isNotEmpty()) {
            val action = remoteMessage.data["action"]
            val payload = remoteMessage.data["payload"]

            Log.d("HandyFCM", "Ricevuto messaggio di matching: Action=$action")

            // Passiamo il payload al dispatcher che avevi già strutturato
            val app = applicationContext as HandyApp
            CoroutineScope(Dispatchers.IO).launch {
                // Se usi FCM, il dispatcher non leggerà più dal socket ma da qui per il matching
                app.realtimeDispatcher.dispatchFcmMessage(action, payload)
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("HandyFCM", "Nuovo token FCM: $token")
        // Qui dovresti inviare il token al tuo server backend tramite Retrofit
        // per permettergli di instradare i broadcast
    }
}
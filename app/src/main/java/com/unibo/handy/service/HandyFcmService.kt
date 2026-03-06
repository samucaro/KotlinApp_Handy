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

/**
 * Servizio Gateway per la ricezione di messaggi asincroni ad alta priorità tramite Firebase Cloud Messaging.
 * Garantisce la ricezione delle Tuple (Help-Request) e dei messaggi di chat anche quando
 * l'applicazione è chiusa o il WebSocket è disconnesso.
 */
@AndroidEntryPoint
class HandyFcmService : FirebaseMessagingService() {

    // Scope dedicato per le operazioni asincrone del servizio.
    // SupervisorJob garantisce che il fallimento di una singola coroutine non faccia crashare l'intero scope.
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Inject lateinit var dispatcher: MessageDispatcher
    @Inject lateinit var userRepository: UserRepository

    /**
     * Intercetta i messaggi push in arrivo.
     * Utilizza i "Data Message" di FCM (invisibili all'utente) per elaborare le tuple in background
     * prima di mostrare eventualmente una notifica locale.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        if (remoteMessage.data.isNotEmpty()) {
            val action = remoteMessage.data["action"]
            val payload = remoteMessage.data["payload"]

            if (action != null && payload != null) {
                // Delega l'elaborazione al Dispatcher in un thread secondario
                serviceScope.launch {
                    dispatcher.dispatch(action, payload)
                }
            } else {
                Log.w("HandyFCM", "Payload o Action mancante nel messaggio FCM")
            }
        }
    }

    /**
     * Chiamato dal sistema quando FCM genera un nuovo token univoco per questa istanza dell'app.
     * Fondamentale per il routing dei messaggi dal backend Python verso questo specifico device.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)

        serviceScope.launch {
            try {
                userRepository.updateFcmToken(token)
            } catch (e: Exception) {
                Log.e("HandyFCM", "Errore nell'aggiornamento del token", e)
            }
        }
    }

    /**
     * Pulizia delle risorse per prevenire Memory Leaks quando il servizio viene distrutto dall'OS.
     */
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
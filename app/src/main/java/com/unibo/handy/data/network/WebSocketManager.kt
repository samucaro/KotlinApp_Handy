package com.unibo.handy.data.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlin.math.pow

/**
 * Macchina a stati finiti che rappresenta lo stato della connettività in tempo reale.
 */
sealed interface NetworkStatus {
    object Initializing : NetworkStatus // Fase di Boot
    object Connected : NetworkStatus // Online
    object Disconnected : NetworkStatus // Offline dopo tentativo
    object Reconnecting : NetworkStatus // Offline prima di tentativo
}

/**
 * Gestore centralizzato per la comunicazione bidirezionale (Full-Duplex) tramite WebSocket.
 * Mantiene la connessione persistente con il server Python e gestisce le disconnessioni involontarie.
 */
class WebSocketManager(private val client: OkHttpClient) {
    private var reconnectAttemptCount = 0
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null

    // Scope dedicato per gestire i tentativi di riconnessione asincroni
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // StateFlow per esporre lo stato della rete alla UI (reattività)
    private val _networkStatus = MutableStateFlow<NetworkStatus>(NetworkStatus.Initializing)
    val networkStatus = _networkStatus.asStateFlow()

    // SharedFlow per emettere i messaggi in arrivo.
    // Usa un buffer circolare per gestire picchi di traffico (Backpressure).
    private val _incomingMessages = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val incomingMessages = _incomingMessages.asSharedFlow()

    private var currentUserId: String? = null
    private var isIntentionalClose = false

    companion object {
        // Indirizzo di loopback per l'emulatore Android verso localhost
        private const val WS_URL = "ws://10.0.2.2:8000/ws/"
    }

    /**
     * Inizia il processo di handshaking TCP/WebSocket.
     * Funzione idempotente: ignora le chiamate se una connessione è già attiva.
     */
    fun connect(idUser: String, isSilentReconnect: Boolean = false) {
        if (webSocket != null) {
            Log.d("HandyWS", "Socket già connesso o in fase di connessione.")
            return
        }

        currentUserId = idUser
        isIntentionalClose = false

        if (!isSilentReconnect && _networkStatus.value != NetworkStatus.Reconnecting) {
            _networkStatus.value = NetworkStatus.Initializing
        }

        val request = Request.Builder()
            .url("$WS_URL$idUser")
            .build()

        Log.i("HandyWS", "Connecting to: $WS_URL$idUser")
        webSocket = client.newWebSocket(request, HandyWebSocketListener())
    }

    /**
     * Invia un payload JSON al server.
     * @return true se il messaggio è stato accodato nel buffer di rete, false altrimenti.
     */
    fun sendMessage(text: String): Boolean {
        val ws = webSocket
        if (ws != null) {
            return ws.send(text)
        } else {
            Log.w("HandyWS", "Impossibile inviare: Socket disconnesso.")
            return false
        }
    }

    /**
     * Termina intenzionalmente la connessione.
     * Previene l'innesco della logica di auto-riconnessione.
     */
    fun close() {
        Log.i("HandyWS", "Chiusura intenzionale della connessione.")
        isIntentionalClose = true
        webSocket?.close(1000, "Logout/App Closed")
        webSocket = null
        currentUserId = null
        _networkStatus.value = NetworkStatus.Disconnected
    }

    /**
     * Forza un reset manuale della rete aggirando i timer di backoff.
     */
    fun resetAndReconnect() {
        Log.w("HandyWS", "Reset manuale della connessione richiesto.")
        isIntentionalClose = false
        reconnectJob?.cancel()
        reconnectAttemptCount = 0

        webSocket?.cancel()
        webSocket = null

        _networkStatus.value = NetworkStatus.Reconnecting
        currentUserId?.let { connect(it) }
    }

    /**
     * Listener interno asincrono che reagisce agli eventi della libreria OkHttp.
     */
    private inner class HandyWebSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            reconnectAttemptCount = 0
            Log.i("HandyWS", "--> CONNESSIONE STABILITA COL SERVER")
            _networkStatus.value = NetworkStatus.Connected
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            // tryEmit è non-bloccante, essenziale perché in un thread di OkHttp
            _incomingMessages.tryEmit(text)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e("HandyWS", "Errore di rete: ${t.message}")
            this@WebSocketManager.webSocket = null
            _networkStatus.value = NetworkStatus.Disconnected

            if (!isIntentionalClose) attemptReconnect()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d("HandyWS", "Connessione chiusa dal server: $reason")
            this@WebSocketManager.webSocket = null
            _networkStatus.value = NetworkStatus.Disconnected

            if (!isIntentionalClose) attemptReconnect()
        }
    }

    /**
     * Implementa un algoritmo di Exponential Backoff per evitare di saturare
     * il server e la batteria dello smartphone con continue richieste di connessione.
     */
    private fun attemptReconnect() {
        if (isIntentionalClose || currentUserId == null) return

        // Formula: 2000ms * 2^tentativi, con un tetto massimo (cap) di 60 secondi
        val backoffDelay = (2000L * 2.0.pow(reconnectAttemptCount.toDouble())).toLong()
            .coerceAtMost(60000L)

        reconnectAttemptCount++
        Log.w("HandyWS", "Nuovo tentativo fra ${backoffDelay / 1000}s (Tentativo: $reconnectAttemptCount)")

        reconnectJob?.cancel()

        // Lo scope dedicato garantisce che il delay non blocchi il Main Thread
        reconnectJob = scope.launch {
            delay(backoffDelay)
            if (!isIntentionalClose && currentUserId != null) {
                webSocket?.cancel()
                webSocket = null
                connect(currentUserId!!, isSilentReconnect = true)
            }
        }
    }
}
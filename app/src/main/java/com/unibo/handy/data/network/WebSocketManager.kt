package com.unibo.handy.data.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

// 3 STATI POSSIBILI DELLA RETE
sealed interface NetworkStatus {
    object Initializing : NetworkStatus  // Fase di Boot
    object Connected : NetworkStatus     // Online
    object Disconnected : NetworkStatus  // Offline dopo tentativo
}

class WebSocketManager(private val client: OkHttpClient) {
    private var reconnectAttemptCount = 0
    private var webSocket: WebSocket? = null
    private var reconnectJob: kotlinx.coroutines.Job? = null
    // Scope per gestire i tentativi di riconnessione, ogni tentativo prende un nuovo thread dal thread pool
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _networkStatus = MutableStateFlow<NetworkStatus>(NetworkStatus.Initializing)
    val networkStatus = _networkStatus.asStateFlow()
    private val _incomingMessages = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val incomingMessages = _incomingMessages.asSharedFlow()

    private var currentUserId: String? = null
    private var isIntentionalClose = false

    // Costante per l'URL (Emulator localhost)
    companion object {
        private const val WS_URL = "ws://10.0.2.2:8000/ws/"
    }

    fun connect(idUser: String) {
        // Idempotenza: se  già connesso o in fase di connessione, evita duplicati
        if (webSocket != null) {
            Log.d("HandyWS", "Socket already connected")
            return
        }

        currentUserId = idUser
        isIntentionalClose = false

        _networkStatus.value = NetworkStatus.Initializing
        Log.d("HandyWS", "--- NUOVO TENTATIVO DI CONNESSIONE LANCIATO ---")

        val request = Request.Builder()
            .url("$WS_URL$idUser")
            .build()

        Log.i("HandyWS", "Connecting to: $WS_URL$idUser")
        webSocket = client.newWebSocket(request, HandyWebSocketListener())
    }

    // Metodo per inviare messaggi di chat tra helepr e richiedente al servere
    fun sendMessage(text: String): Boolean {
        val ws = webSocket
        if (ws != null) {
            return ws.send(text)
        } else {
            Log.w("HandyWS", "Socket not connected")
            return false
        }
    }

    fun close() {
        Log.i("HandyWS", "Closing connection")
        isIntentionalClose = true
        webSocket?.close(1000, "Logout/App Closed")
        webSocket = null
        currentUserId = null
        _networkStatus.value = NetworkStatus.Disconnected
    }

    fun resetAndReconnect() {
        Log.w("HandyWS", "Manual resetting connection")
        isIntentionalClose = false

        // 1. CANCELLA l'eventuale riconnessione automatica in attesa
        reconnectJob?.cancel()
        // 2. AZZERA il contatore per la demo, così riparte subito senza aspettare minuti
        reconnectAttemptCount = 0

        // Se l'utente clicca il bottone manuale, forza lo stato Initializing
        // per dargli un feedback visivo che sta provando
        webSocket?.cancel()
        webSocket = null

        _networkStatus.value = NetworkStatus.Initializing

        currentUserId?.let { connect(it) }
    }

    // Inner class per pulizia del codice
    private inner class HandyWebSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            reconnectAttemptCount = 0
            Log.i("HandyWS", "--> CONNECTED TO SERVER")
            _networkStatus.value = NetworkStatus.Connected
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            // Emette il messaggio nel Flow. Il Dispatcher lo raccoglierà.
            _incomingMessages.tryEmit(text)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e("HandyWS", "Connection error: ${t.message}")
            this@WebSocketManager.webSocket = null

            _networkStatus.value = NetworkStatus.Disconnected

            if (!isIntentionalClose) {
                attemptReconnect()
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d("HandyWS", "Connection closed: $reason")
            this@WebSocketManager.webSocket = null

            _networkStatus.value = NetworkStatus.Disconnected

            if (!isIntentionalClose) {
                attemptReconnect()
            }
        }
    }

    private fun attemptReconnect() {
        if (isIntentionalClose || currentUserId == null) return

        // Backoff esponenziale: 3s, 6s, 12s, 24s... massimo 1 minuto
        val backoffDelay = (3000L * 2.0.pow(reconnectAttemptCount.toDouble())).toLong()
            .coerceAtMost(60000L) // Cap a 60 secondi

        reconnectAttemptCount++
        Log.w("HandyWS", "Reconnection try ${backoffDelay/1000}s (Tentative: $reconnectAttemptCount)")

        // Cancella eventuali job precedenti per sicurezza
        reconnectJob?.cancel()

        reconnectJob = scope.launch {
            Log.w("HandyWS", "Riconnection attempt...")
            delay(backoffDelay)
            if (!isIntentionalClose && currentUserId != null) {
                webSocket?.cancel()
                // Riprova ricorsivamente (ma in un nuovo thread grazie a launch)
                // Impostare webSocket a null prima di chiamare connect non serve se gestito nei listener,
                // ma per sicurezza  assicura che sia pulito
                webSocket = null
                connect(currentUserId!!)
            }
        }
    }
}
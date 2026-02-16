package com.unibo.handy.data.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class WebSocketManager(private val client: OkHttpClient) {
    private var webSocket: WebSocket? = null
    // Scope per gestire i tentativi di riconnessione, ogni tentativo prende un nuovo thread dal thread pool
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // SharedFlow per broadcast dei messaggi al Dispatcher
    // onBufferOverflow = DROP_OLDEST assicura che non si blocchi mai
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
    }

    fun isConnected(): Boolean = webSocket != null

    // Inner class per pulizia del codice
    private inner class HandyWebSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i("HandyWS", "--> CONNECTED TO SERVER")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            // Emette il messaggio nel Flow. Il Dispatcher lo raccoglierà.
            _incomingMessages.tryEmit(text)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e("HandyWS", "Connection error: ${t.message}")
            this@WebSocketManager.webSocket = null // Reset

            if (!isIntentionalClose) {
                attemptReconnect()
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d("HandyWS", "Connection closed: $reason")
            this@WebSocketManager.webSocket = null

            if (!isIntentionalClose) {
                attemptReconnect()
            }
        }
    }

    private fun attemptReconnect() {
        scope.launch {
            Log.w("HandyWS", "Riconnection attempt...")
            delay(3000)
            if (!isIntentionalClose && currentUserId != null) {
                // Riprova ricorsivamente (ma in un nuovo thread grazie a launch)
                // Impostare webSocket a null prima di chiamare connect non serve se gestito nei listener,
                // ma per sicurezza  assicura che sia pulito
                this@WebSocketManager.webSocket = null
                connect(currentUserId!!)
            }
        }
    }
}
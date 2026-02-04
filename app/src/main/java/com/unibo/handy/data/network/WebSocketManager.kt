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
    // Scope per gestire i tentativi di riconnessione, ogni tentativo prende un nuovo thread
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    // onBufferOverflow = DROP_OLDEST assicura che non si blocchi mai
    private val _incomingMessages = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val incomingMessages = _incomingMessages.asSharedFlow()

    private var currentUserId: String? = null
    private var isIntentionalClose = false

    fun connect(idUser: String) {
        if (webSocket != null) return

        currentUserId = idUser
        isIntentionalClose = false

        val request = Request.Builder()
            .url("ws://10.0.2.2:8000/ws/$idUser")
            .build()

        webSocket = client.newWebSocket(request, createListener())
    }

    private fun createListener() : WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("HandyWS", "Connected to server")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("HandyWS", "Message receive: $text")
                // Non blocca il thread in attesa della collect,
                // se nessuno ascolta il messaggio viene perso
                _incomingMessages.tryEmit(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("HandyWS", "Connection error", t)
                webSocket.close(1000, null)
                this@WebSocketManager.webSocket = null

                if(!isIntentionalClose) {
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
    }

    private fun attemptReconnect() {
        coroutineScope.launch {
            Log.w("HandyWS", "")
            delay(3000)
            currentUserId?.let {
                Log.d("HandyWS", "")
                connect(it)
            }
        }
    }

    fun sendMessage(text: String) {
        webSocket?.send(text) ?: Log.w("HandyWS", "")
    }

    fun close() {
        isIntentionalClose = true
        webSocket?.close(1000, "App closing")
        webSocket = null
    }
}
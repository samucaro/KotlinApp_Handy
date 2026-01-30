package com.unibo.handy.data.network

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class WebSocketManager {
    private val user = OkHttpClient()
    private var webSocket: WebSocket? = null
    private val gson = Gson()

    private val _incomingMessages = MutableSharedFlow<String>()
    val incomingMessages = _incomingMessages.asSharedFlow()

    fun connect(idUser: String) {
        val request = Request.Builder()
            .url("ws://10.0.2.2:8000/ws/$idUser")
            .build()

        webSocket = user.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("HandyWS", "Connected to server")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("HandyWS", "Message receive: $text")
                _incomingMessages.tryEmit(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("HandyWS", "Connection error", t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("HandyWS", "Connection closed: $reason")
            }
        })
    }

    fun sendMessage(text: String) {
        webSocket?.send(text)
    }

    fun close() {
        webSocket?.close(1000, "App closing")
    }
}
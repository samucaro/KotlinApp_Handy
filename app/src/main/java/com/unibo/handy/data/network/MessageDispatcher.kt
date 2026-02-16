package com.unibo.handy.data.network

import android.util.Log
import com.google.gson.JsonParser
import com.unibo.handy.data.repository.strategy.MessageStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// Agisce come un 'vigile urbano': ascolta il flusso grezzo di messaggi in arrivo dal socket,
// ne ispeziona l'intestazione (il campo type) e instrada il payload al Repository competente
class MessageDispatcher(
    private val webSocketManager: WebSocketManager,
    private val handlers: Map<String, MessageStrategy>
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var dispatchJob: Job? = null

    //Avvia l'ascolto dei messaggi in arrivo dal WebSocket
    fun startDispatching() {
        if (dispatchJob?.isActive == true) return

        dispatchJob = scope.launch {
            webSocketManager.incomingMessages.collectLatest { jsonString ->
                routeMessage(jsonString)
            }
        }
    }

    fun stopDispatching() {
        dispatchJob?.cancel()
        dispatchJob = null
    }

    private suspend fun routeMessage(json: String) {
        try {
            // 1. Parsing preliminare solo per ottenere "type" e "payload" come JsonElement
            val root = JsonParser.parseString(json).asJsonObject
            val type = root.get("type")?.asString
            val payload = root.get("payload")

            if (type == null || payload == null) {
                Log.w("Dispatcher", "Message type or payload missing")
                return
            }

            // 2. Routing del payload al Repository specifico
            val handler = handlers[type]

            if (handler != null) {
                // Delega l'esecuzione alla strategia concreta
                handler.handle(payload)
            } else {
                Log.w("Dispatcher", "Handler not found for type: $type")
            }

        } catch (e: Exception) {
            Log.e("Dispatcher", "Error in routeMessage", e)
        }
    }
}
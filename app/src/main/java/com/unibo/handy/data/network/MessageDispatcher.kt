package com.unibo.handy.data.network

import android.util.Log
import com.unibo.handy.data.repository.strategy.MessageStrategy

// Agisce come un 'vigile urbano': ascolta il flusso grezzo di messaggi in arrivo dal socket,
// ne ispeziona l'intestazione (il campo type) e instrada il payload al Repository competente
class MessageDispatcher(
    private val handlers: Map<String, MessageStrategy>
) {
    suspend fun dispatch(action: String, payload: String) {
        // Routing del payload al Repository specifico
        val handler = handlers[action]
        if (handler != null) {
            Log.d("MessageDispatcher", "Esecuzione handler per l'azione: $action")
            try {
                handler.handle(payload)
            } catch (e: Exception) {
                Log.e("MessageDispatcher", "Errore durante l'esecuzione dell'handler per $action", e)
            }
        } else {
            Log.w("MessageDispatcher", "Nessun handler registrato per l'azione: $action")
        }
    }
}
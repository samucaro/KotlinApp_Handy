package com.unibo.handy.data.network

import android.util.Log
import com.unibo.handy.data.network.strategy.MessageStrategy

/**
 * Componente di Routing (Front Controller) per l'infrastruttura di rete asincrona.
 * Ascolta il flusso grezzo di messaggi in arrivo (da WebSocket o FCM),
 * ne ispeziona l'intestazione (action/type) e instrada il payload al livello di competenza.
 */
class MessageDispatcher(
    // La mappa viene tipicamente iniettata da Hilt tramite il meccanismo di Multibindings (@IntoMap)
    private val handlers: Map<String, @JvmSuppressWildcards MessageStrategy>
) {
    /**
     * Risolve l'azione richiesta ed esegue la strategia corrispondente.
     * * @param action La stringa che identifica il tipo di messaggio (es. "HELP_REQUEST", "CHAT_MESSAGE")
     * @param payload Il corpo del messaggio in formato JSON (ancora da decodificare)
     */
    suspend fun dispatch(action: String, payload: String) {
        val handler = handlers[action]

        if (handler != null) {
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
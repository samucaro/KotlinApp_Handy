package com.unibo.handy.data.network.strategy

import android.util.Log
import com.google.gson.Gson
import com.unibo.handy.data.network.dto.ChatMessageDTO
import com.unibo.handy.data.repository.ChatRepository
import javax.inject.Inject

/**
 * Strategia per l'elaborazione dei messaggi di testo peer-to-peer (P2P simulato) post-match.
 */
class ChatMessageStrategy @Inject constructor(
    private val chatRepo: ChatRepository,
    private val gson: Gson
) : MessageStrategy {

    override suspend fun handle(payload: String) {
        try {
            val messageData = gson.fromJson(payload, ChatMessageDTO::class.java)
            chatRepo.saveIncomingMessage(messageData.from, messageData.message)
        } catch (e: Exception) {
            Log.e("ChatMessageStrategy", "Errore parsing JSON chat: ${e.message}")
        }
    }
}
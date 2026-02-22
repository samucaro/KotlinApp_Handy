package com.unibo.handy.data.repository.strategy

import com.google.gson.Gson
import com.unibo.handy.data.network.dto.ChatMessageDTO
import com.unibo.handy.data.repository.ChatRepository

class ChatMessageStrategy(
    private val chatRepo: ChatRepository,
    private val gson: Gson
) : MessageStrategy {
    override suspend fun handle(payload: String) {
        // Parsing specifico per la chat
        val messageData = gson.fromJson(payload, ChatMessageDTO::class.java)
        chatRepo.saveIncomingMessage(messageData.from, messageData.message)
    }
}
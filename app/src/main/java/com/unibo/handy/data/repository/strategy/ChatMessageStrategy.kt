package com.unibo.handy.data.repository.strategy

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.unibo.handy.data.network.dto.ChatMessageDTO
import com.unibo.handy.data.repository.ChatRepository

class ChatMessageStrategy(
    private val chatRepository: ChatRepository,
    private val gson: Gson
) : MessageStrategy {
    override suspend fun handle(payload: JsonElement) {
        // Parsing specifico per la chat
        val messageData = gson.fromJson(payload, ChatMessageDTO::class.java)
        chatRepository.saveIncomingMessage(messageData.from, messageData.message)
    }
}
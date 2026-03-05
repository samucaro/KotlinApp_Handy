package com.unibo.handy.data.network.strategy

import com.google.gson.Gson
import com.unibo.handy.data.network.dto.ChatMessageDTO
import com.unibo.handy.data.repository.ChatRepository

class ChatMessageStrategy(
    private val chatRepo: ChatRepository,
    private val gson: Gson
) : MessageStrategy {
    override suspend fun handle(payload: String) {
        val messageData = gson.fromJson(payload, ChatMessageDTO::class.java)
        chatRepo.saveIncomingMessage(messageData.from, messageData.message)
    }
}
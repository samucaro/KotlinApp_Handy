package com.unibo.handy.data.repository

import com.google.gson.Gson
import com.unibo.handy.data.db.dao.ChatDAO
import com.unibo.handy.data.db.dao.UserDAO
import com.unibo.handy.data.db.entity.ChatMessagesEntity
import com.unibo.handy.data.network.WebSocketManager
import kotlinx.coroutines.flow.Flow

class ChatRepository(
    private val chatDao: ChatDAO,
    private val userDao: UserDAO,
    private val webSocketManager: WebSocketManager
) {
    private val gson = Gson()

    // 1. Ottieni i messaggi (dal DB locale)
    fun getMessagesFlow(chatId: String): Flow<List<ChatMessagesEntity>> {
        return chatDao.getMessages(chatId)
    }

    // 2. Invia messaggio (Salva locale + Invia WebSocket)
    suspend fun sendMessage(recipientId: String, content: String) {
        val currentUser = userDao.getUserSnapshot() ?: return

        // A. Salva nel DB Locale (Feedback immediato UI)
        val msgEntity = ChatMessagesEntity(
            chatId = recipientId,
            senderId = currentUser.userId,
            message = content,
            timestamp = System.currentTimeMillis()
        )
        chatDao.insertMessage(msgEntity)

        // B. Invia al Server
        val payload = mapOf(
            "type" to "CHAT_MESSAGE",
            "payload" to mapOf(
                "to" to recipientId,
                "message" to content
            )
        )
        webSocketManager.sendMessage(gson.toJson(payload))
    }

    // 3. Salva messaggio ricevuto (chiamato quando arriva da fuori)
    suspend fun saveIncomingMessage(senderId: String, content: String) {
        val entity = ChatMessagesEntity(
            chatId = senderId, // La chat è con chi mi ha scritto
            senderId = senderId,
            message = content,
            timestamp = System.currentTimeMillis()
        )
        chatDao.insertMessage(entity)
    }
}
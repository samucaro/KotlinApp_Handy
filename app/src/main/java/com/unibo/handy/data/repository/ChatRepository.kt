package com.unibo.handy.data.repository

import com.google.gson.Gson
import com.unibo.handy.data.db.dao.ChatDAO
import com.unibo.handy.data.db.dao.MatchDAO
import com.unibo.handy.data.db.dao.UserDAO
import com.unibo.handy.data.db.entity.ChatMessagesEntity
import com.unibo.handy.data.db.entity.MatchStatus
import com.unibo.handy.data.network.WebSocketManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository dedicato alla gestione delle comunicazioni real-time (Chat) post-match.
 * Funge da mediatore tra il database locale (Room) e l'infrastruttura di rete asincrona (WebSocket).
 */
@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDAO,
    private val userDao: UserDAO,
    private val matchDao: MatchDAO,
    private val webSocketManager: WebSocketManager
) {
    private val gson = Gson()

    /**
     * Quando l'Helper accetta il match, aggiorna il database locale
     * e genera un messaggio di sistema per avviare la conversazione.
     */
    suspend fun acceptMatch(matchId: String, requesterId: String) {
        matchDao.updateStatus(matchId, MatchStatus.ACCEPTED)

        // Invia notifica via WebSocket al requester (P2P simulato via Server)
        val payload = mapOf(
            "type" to "CHAT_MESSAGE",
            "payload" to mapOf(
                "to" to requesterId,
                "message" to "SYSTEM: Richiesta Accettata! Ora potete chattare."
            )
        )
        webSocketManager.sendMessage(gson.toJson(payload))

        // Crea il messaggio di sistema locale per dare feedback immediato all'Helper
        saveIncomingMessage(requesterId, "Hai accettato la richiesta. Inizia a chattare!")
    }

    suspend fun rejectMatch(matchId: String) {
        matchDao.updateStatus(matchId, MatchStatus.REJECTED)
    }

    fun getMessagesFlow(chatId: String): Flow<List<ChatMessagesEntity>> {
        return chatDao.getMessages(chatId)
    }

    suspend fun sendMessage(recipientId: String, content: String)= withContext(Dispatchers.IO) {
        val currentUser = userDao.getUserSnapshot() ?: return@withContext

        // 1. Persistenza Locale (Messaggio Inviato)
        val msgEntity = ChatMessagesEntity(
            chatId = recipientId,
            senderId = currentUser.userId,
            message = content,
            isSync = false,
            timestamp = System.currentTimeMillis()
        )

        val generatedRowId = chatDao.insertMessage(msgEntity)

        // 2. Trasmissione al Server
        val payload = mapOf(
            "type" to "CHAT_MESSAGE",
            "payload" to mapOf(
                "to" to recipientId,
                "message" to content
            )
        )

        val sent = webSocketManager.sendMessage(gson.toJson(payload))
        if (sent) {
            chatDao.markMessageAsSynced(generatedRowId)
        }
    }

    /**
     * Svuota la coda dei messaggi in sospeso.
     * Si invoca quando il WebSocketManager segnala che la connessione è stata ristabilita.
     */
    suspend fun syncPendingMessages() = withContext(Dispatchers.IO) {
        val pendingMessages = chatDao.getUnsyncedMessages()

        if (pendingMessages.isEmpty()) return@withContext

        for (msg in pendingMessages) {
            val payload = mapOf(
                "type" to "CHAT_MESSAGE",
                "payload" to mapOf("to" to msg.chatId, "message" to msg.message)
            )

            val sent = webSocketManager.sendMessage(gson.toJson(payload))
            if (sent) {
                chatDao.markMessageAsSynced(msg.id)
            } else {
                break // Se fallisce il primo, inutile provare gli altri, la rete è di nuovo giù
            }
        }
    }

    suspend fun saveIncomingMessage(senderId: String, content: String) {
        val entity = ChatMessagesEntity(
            chatId = senderId,
            senderId = senderId,
            message = content,
            isSync = true,
            timestamp = System.currentTimeMillis()
        )
        chatDao.insertMessage(entity)
    }
}
package com.unibo.handy.data.repository

import android.util.Log
import com.google.gson.Gson
import com.unibo.handy.data.db.dao.ChatDAO
import com.unibo.handy.data.db.dao.MatchDAO
import com.unibo.handy.data.db.dao.UserDAO
import com.unibo.handy.data.db.entity.ChatMessagesEntity
import com.unibo.handy.data.db.entity.MatchStatus
import com.unibo.handy.data.network.WebSocketManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ChatRepository @Inject constructor(
    private val chatDao: ChatDAO,
    private val userDao: UserDAO,
    private val matchDao: MatchDAO,
    private val webSocketManager: WebSocketManager
) {
    private val gson = Gson()

    /**
     * Quando l'Helper clicca su "Accetta" del popup o nell'Activity.
     * Trasforma la richiesta in una chat attiva.
     */
    suspend fun acceptMatch(requesterId: String) {
        Log.i("ChatRepo", "Accepting match for: $requesterId")

        // Aggiorna lo stato della richiesta
        matchDao.updateStatus(requesterId, MatchStatus.ACCEPTED)

        // Invia notifica via WebSocket al requester
        val payload = mapOf(
            "type" to "CHAT_MESSAGE",
            "payload" to mapOf(
                "to" to requesterId,
                "message" to "SYSTEM: Richiesta Accettata! Ora potete chattare."
            )
        )
        webSocketManager.sendMessage(gson.toJson(payload))

        // 3. Crea messaggio di sistema locale
        saveIncomingMessage(requesterId, "Hai accettato la richiesta. Inizia a chattare!")
    }

    suspend fun rejectMatch(requesterId: String) {
        matchDao.updateStatus(requesterId, MatchStatus.REJECTED)
    }

    fun getMessagesFlow(chatId: String): Flow<List<ChatMessagesEntity>> {
        return chatDao.getMessages(chatId)
    }

    suspend fun sendMessage(recipientId: String, content: String) {
        val currentUser = userDao.getUserSnapshot() ?: return

        // A. Salva nel DB Locale (messaggio inviato da l'utente)
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
        val sent = webSocketManager.sendMessage(gson.toJson(payload))
        if (!sent) {
            Log.w("ChatRepo", "Message not sent")
            // aggiornare lo stato del messaggio a "NON INVIATO" nel DB nel caso
        }
    }

    // Salva messaggio ricevuto
    suspend fun saveIncomingMessage(senderId: String, content: String) {
        val entity = ChatMessagesEntity(
            chatId = senderId,
            senderId = senderId,
            message = content,
            timestamp = System.currentTimeMillis()
        )
        chatDao.insertMessage(entity)
        Log.v("ChatRepo", "Incoming message saved from: $senderId")
    }
}
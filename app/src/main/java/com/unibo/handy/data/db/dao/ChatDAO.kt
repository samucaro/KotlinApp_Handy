package com.unibo.handy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.unibo.handy.data.db.entity.ChatMessagesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDAO {
    @Insert
    suspend fun insertMessage(message: ChatMessagesEntity): Long
    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessages(chatId: String): Flow<List<ChatMessagesEntity>>
    @Query("SELECT * FROM chat_messages WHERE isSync = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedMessages(): List<ChatMessagesEntity>
    @Query("UPDATE chat_messages SET isSync = 1 WHERE id = :messageId")
    suspend fun markMessageAsSynced(messageId: Long)
}
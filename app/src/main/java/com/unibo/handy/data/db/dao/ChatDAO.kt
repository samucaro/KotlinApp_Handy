package com.unibo.handy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.unibo.handy.data.db.entity.ChatMessagesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDAO {
    @Insert
    suspend fun insertMessage(message: ChatMessagesEntity)

    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessages(chatId: String): Flow<List<ChatMessagesEntity>>
}
package com.unibo.handy.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessagesEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: String,
    val senderId: String,
    val message: String,
    val isSync: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

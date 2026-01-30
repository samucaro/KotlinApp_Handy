package com.unibo.handy.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "user")
data class UserEntity(
    @PrimaryKey val userId: String = UUID.randomUUID().toString(),
    val username: String,
    val category: String,
    val helpModeActive: Boolean = false,

    val createdAt: Long = System.currentTimeMillis()
)

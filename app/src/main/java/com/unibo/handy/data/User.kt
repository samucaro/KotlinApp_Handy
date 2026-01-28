package com.unibo.handy.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "users")
data class User(
    @PrimaryKey val userId: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis()
)

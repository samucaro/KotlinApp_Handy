package com.unibo.handy.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "user")
data class UserEntity(
    @PrimaryKey val userId: String = UUID.randomUUID().toString(),

    val createdAt: Long = System.currentTimeMillis()
)

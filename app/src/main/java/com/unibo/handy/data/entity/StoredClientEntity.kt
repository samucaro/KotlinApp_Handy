package com.unibo.handy.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stored_clients")
data class StoredClientEntity(
    @PrimaryKey val clientId: String,

    val blurredX: Long,
    val blurredY: Long,
    val category: String,

    val timestamp: Long = System.currentTimeMillis()
)

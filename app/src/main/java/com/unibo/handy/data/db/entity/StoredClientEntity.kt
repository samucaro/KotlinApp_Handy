package com.unibo.handy.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stored_clients")
data class StoredClientEntity(
    @PrimaryKey val clientId: String,

    val reblurredX: Long,
    val reblurredY: Long,
    val category: String,

    val timestamp: Long = System.currentTimeMillis()
)

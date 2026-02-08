package com.unibo.handy.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MatchStatus {
    PENDING,
    ACCEPTED,
    REJECTED
}

@Entity(tableName = "matches")
data class MatchEntity(
    @PrimaryKey val requesterId: String,
    val helperId: String,
    val username: String,
    val category: String,
    val phoneNumber: String,
    val status: MatchStatus = MatchStatus.PENDING,
    val timestamp: Long = System.currentTimeMillis()
)

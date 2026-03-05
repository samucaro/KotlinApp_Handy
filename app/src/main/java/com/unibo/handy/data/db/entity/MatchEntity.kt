package com.unibo.handy.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class MatchStatus {
    PENDING,
    ACCEPTED,
    REJECTED
}

@Entity(tableName = "matches")
data class MatchEntity(
    @PrimaryKey val matchId: String = UUID.randomUUID().toString(),
    val requesterId: String,
    val helperId: String,
    val username: String,
    val category: String,
    val phoneNumber: String,
    val status: MatchStatus = MatchStatus.PENDING,
    val isMeHelper: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

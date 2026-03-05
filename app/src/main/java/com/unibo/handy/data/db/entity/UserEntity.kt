package com.unibo.handy.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.unibo.handy.domain.model.User
import java.util.UUID

@Entity(tableName = "user")
data class UserEntity(
    @PrimaryKey val userId: String = UUID.randomUUID().toString(),

    val username: String,
    val email: String,
    val passwordHash: String,
    val category: String,
    val helpModeActive: Boolean = false,
    val rating: Int = 0
)

// Extension function per mappare l'Entity nel modello di Dominio
fun UserEntity.toDomain(): User {
    return User(
        userId = this.userId,
        username = this.username,
        email = this.email,
        category = this.category,
        helpModeActive = this.helpModeActive
    )
}

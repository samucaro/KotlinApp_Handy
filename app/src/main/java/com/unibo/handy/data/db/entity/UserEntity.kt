package com.unibo.handy.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "user")
data class UserEntity(
    @PrimaryKey val userId: String = UUID.randomUUID().toString(),

    val username: String,
    val email: String,
    val passwordHash: String,
    val category: String,
    val helpModeActive: Boolean = false,
    // Possibili ulteriori dati da aggiungere
    // val myReviews: String = "[]", //elenco di recensioni che ha rilasciato l'utente

    // --- DATI SPECIFICI AIUTANTE ---
    // Media voti (resta 0 se utente user invece di helper)
    val rating: Int = 0,
    // val receivedReviews: String = "[]" //elenco di recensioni ricevute dagli utenti
)

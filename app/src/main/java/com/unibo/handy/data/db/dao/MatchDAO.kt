package com.unibo.handy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.unibo.handy.data.db.entity.MatchEntity
import com.unibo.handy.data.db.entity.MatchStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MatchDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatch(match: MatchEntity)

    // Query per la schermata "Activity" (Solo le richieste in attesa)
    @Query("SELECT * FROM matches WHERE status = 'PENDING' ORDER BY timestamp DESC")
    fun getPendingMatches(): Flow<List<MatchEntity>>

    // Query per la schermata "Chat" (Solo match accettati)
    @Query("SELECT * FROM matches WHERE status = 'ACCEPTED' ORDER BY timestamp DESC")
    fun getActiveChats(): Flow<List<MatchEntity>>

    // Funzione per cambiare stato (Accept/Reject)
    @Query("UPDATE matches SET status = :newStatus WHERE requesterId = :matchId")
    suspend fun updateStatus(matchId: String, newStatus: MatchStatus)
}
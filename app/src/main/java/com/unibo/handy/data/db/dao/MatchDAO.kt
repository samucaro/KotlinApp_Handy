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
    // Attività: Solo le richieste ricevute in veste di Helper
    @Query("SELECT * FROM matches WHERE status = 'PENDING' ORDER BY timestamp DESC")
    fun getPendingMatches(): Flow<List<MatchEntity>>
    // Chat: I lavori che ha accettato di fare (Helper)
    @Query("SELECT * FROM matches WHERE status = 'ACCEPTED' AND isMeHelper = 1 ORDER BY timestamp DESC")
    fun getActiveChatsAsHelper(): Flow<List<MatchEntity>>
    // Chat: I lavori che ha richiesto agli altri (Richiedente)
    @Query("SELECT * FROM matches WHERE status = 'ACCEPTED' AND isMeHelper = 0 ORDER BY timestamp DESC")
    fun getActiveChatsAsRequester(): Flow<List<MatchEntity>>
    // Funzione per cambiare stato (Accept/Reject)
    @Query("UPDATE matches SET status = :newStatus WHERE matchId = :matchId")
    suspend fun updateStatus(matchId: String, newStatus: MatchStatus)
}
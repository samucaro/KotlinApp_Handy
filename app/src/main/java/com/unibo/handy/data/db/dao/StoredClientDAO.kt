package com.unibo.handy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.unibo.handy.data.db.entity.StoredClientEntity

@Dao
interface StoredClientDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: StoredClientEntity)

    @Query("SELECT * FROM stored_clients WHERE clientId = :clientId")
    suspend fun getProfile(clientId: String): StoredClientEntity?

    @Query("SELECT * FROM stored_clients")
    suspend fun getAllClients(): List<StoredClientEntity>

    @Query("""
        UPDATE stored_clients
        SET reblurred_x = :betaX,
            reblurred_y = :betaY
        WHERE clientId = :clientId
    """)
    suspend fun updatePosition(clientId: String, betaX: Long, betaY: Long)

    @Query("""
        UPDATE stored_clients 
        SET rating = :newRating 
        WHERE clientId = :clientId
    """)
    suspend fun updateRatingData(clientId: String, newRating: Int)

    @Query("SELECT * FROM stored_clients WHERE category = :requiredCategory")
    suspend fun getProfilesByCategory(requiredCategory: String): List<StoredClientEntity>

    @Query("DELETE FROM stored_clients WHERE clientId = :clientId")
    suspend fun deleteProfile(clientId: String)

}
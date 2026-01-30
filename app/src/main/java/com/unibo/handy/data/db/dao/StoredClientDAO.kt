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

    @Query("SELECT * FROM stored_clients WHERE category = :requiredCategory")
    suspend fun getProfilesByCategory(requiredCategory: String): List<StoredClientEntity>

    @Query("DELETE FROM stored_clients WHERE clientId = :clientId")
    suspend fun deleteProfile(clientId: String)

}
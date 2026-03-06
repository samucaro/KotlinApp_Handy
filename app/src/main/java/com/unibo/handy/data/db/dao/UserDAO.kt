package com.unibo.handy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.unibo.handy.data.db.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)
    @Query("SELECT * FROM user LIMIT 1")
    fun getUserFlow(): Flow<UserEntity?>
    @Query("SELECT * FROM user LIMIT 1")
    suspend fun getUserSnapshot(): UserEntity?
    @Query("UPDATE user SET helpModeActive = :isActive WHERE userId = :clientId")
    suspend fun updateHelperMode(clientId: String, isActive: Boolean)
    @Query("UPDATE user SET rating = :newRating WHERE userId = :clientId")
    suspend fun updateMyReputation(clientId: String, newRating: Int)
}
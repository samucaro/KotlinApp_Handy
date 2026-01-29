package com.unibo.handy.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.unibo.handy.data.dao.StoredClientDAO
import com.unibo.handy.data.entity.StoredClientEntity
import com.unibo.handy.data.entity.UserEntity
import com.unibo.handy.data.dao.UserDAO

@Database(entities = [UserEntity::class, StoredClientEntity::class], version = 2, exportSchema = true)
abstract class HandyDB : RoomDatabase() {
    // per utilizzare le query sull'oggetto che implementa l'interfaccia
    abstract fun userDao(): UserDAO
    abstract fun storedClientDao(): StoredClientDAO


    companion object {
        @Volatile
        private var INSTANCE: HandyDB? = null

        fun getDatabase(context: Context): HandyDB {
            // Permette di evitare la creazione contemporanea del db
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HandyDB::class.java,
                    "handy_db"
                ).build()
                INSTANCE = instance
                instance
            }

        }
    }
}
package com.unibo.handy.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.unibo.handy.data.db.dao.ChatDAO
import com.unibo.handy.data.db.dao.MatchDAO
import com.unibo.handy.data.db.dao.StoredClientDAO
import com.unibo.handy.data.db.entity.StoredClientEntity
import com.unibo.handy.data.db.entity.UserEntity
import com.unibo.handy.data.db.dao.UserDAO
import com.unibo.handy.data.db.entity.ChatMessagesEntity
import com.unibo.handy.data.db.entity.MatchEntity
import androidx.room.TypeConverters

@Database(
    entities = [
        UserEntity::class,
        StoredClientEntity::class,
        MatchEntity::class,
        ChatMessagesEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(HandyTypeConverters::class)
abstract class HandyDB : RoomDatabase() {
    // per utilizzare le query sull'oggetto che implementa l'interfaccia
    abstract fun userDao(): UserDAO
    abstract fun storedClientDao(): StoredClientDAO
    abstract fun matchDao(): MatchDAO
    abstract fun chatDao(): ChatDAO


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
                ).fallbackToDestructiveMigration(false).build()
                INSTANCE = instance
                instance
            }

        }
    }
}
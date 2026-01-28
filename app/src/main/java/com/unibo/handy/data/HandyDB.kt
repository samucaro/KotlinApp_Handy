package com.unibo.handy.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [User::class], version = 1, exportSchema = false)
abstract class HandyDB : RoomDatabase() {
    abstract fun userDao(): UserDAO

    companion object {
        @Volatile
        private var INSTANCE: HandyDB? = null

        fun getDatabase(context: Context): HandyDB {
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
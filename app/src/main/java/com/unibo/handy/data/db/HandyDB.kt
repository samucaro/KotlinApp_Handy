package com.unibo.handy.data.db

import androidx.room.Database
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

/**
 * Entry point principale per la persistenza dei dati relazionali (SQLite).
 * Implementa il pattern Object-Relational Mapping (ORM) tramite la libreria Jetpack Room.
 */
@Database(
    entities = [
        UserEntity::class,
        StoredClientEntity::class,
        MatchEntity::class,
        ChatMessagesEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(HandyTypeConverters::class)
abstract class HandyDB : RoomDatabase() {

    // Esposizione dei DAO (Data Access Objects) per l'interazione con le tabelle
    abstract fun userDao(): UserDAO
    abstract fun storedClientDao(): StoredClientDAO
    abstract fun matchDao(): MatchDAO
    abstract fun chatDao(): ChatDAO
}
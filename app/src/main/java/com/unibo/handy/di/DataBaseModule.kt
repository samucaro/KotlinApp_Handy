package com.unibo.handy.di

import android.content.Context
import androidx.room.Room
import com.unibo.handy.data.db.HandyDB
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class) // Gli oggetti vivranno per tutta la vita dell'app
object DataBaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HandyDB {
        return Room.databaseBuilder(
            context,
            HandyDB::class.java,
            "handy_db"
        ).fallbackToDestructiveMigration(false).build()
    }

    // Insegna a Hilt come estrarre i DAO
    @Provides
    fun provideUserDao(db: HandyDB) = db.userDao()

    @Provides
    fun provideMatchDao(db: HandyDB) = db.matchDao()

    @Provides
    fun provideStoredClientDao(db: HandyDB) = db.storedClientDao()

    @Provides
    fun provideChatDao(db: HandyDB) = db.chatDao()
}
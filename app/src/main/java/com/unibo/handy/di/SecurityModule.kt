package com.unibo.handy.di

import android.content.Context
import com.unibo.handy.data.repository.SecureKeyRepository
import com.unibo.handy.data.security.CryptoManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {
    @Provides
    @Singleton
    fun provideCryptoManager(): CryptoManager {
        return CryptoManager()
    }

    @Provides
    @Singleton
    fun provideSecureKeyRepository(
        @ApplicationContext context: Context,
        cryptoManager: CryptoManager
    ): SecureKeyRepository {
        return SecureKeyRepository(context, cryptoManager)
    }
}
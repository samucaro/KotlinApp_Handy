package com.unibo.handy.di

import com.google.gson.Gson
import com.unibo.handy.data.network.MessageDispatcher
import com.unibo.handy.data.repository.ChatRepository
import com.unibo.handy.data.repository.MatchingRepository
import com.unibo.handy.data.repository.strategy.ChatMessageStrategy
import com.unibo.handy.data.repository.strategy.ComputeMatchStrategy
import com.unibo.handy.data.repository.strategy.MatchFoundStrategy
import com.unibo.handy.data.repository.strategy.StoreProfileStrategy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideMessageDispatcher(
        matchingRepository: MatchingRepository,
        chatRepository: ChatRepository,
        gson: Gson
    ): MessageDispatcher {
        val handlersMap = mapOf(
            "COMPUTE_MATCH" to ComputeMatchStrategy(matchingRepository, gson),
            "STORE_PROFILE" to StoreProfileStrategy(matchingRepository, gson),
            "UPDATE_PROFILE" to StoreProfileStrategy(matchingRepository, gson),
            "CHAT_MESSAGE" to ChatMessageStrategy(chatRepository, gson),
            "MATCH_FOUND" to MatchFoundStrategy(matchingRepository, gson)
        )
        return MessageDispatcher(handlersMap)
    }
}
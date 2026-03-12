package com.unibo.handy.di

import com.google.gson.Gson
import com.unibo.handy.data.network.MessageDispatcher
import com.unibo.handy.data.repository.ChatRepository
import com.unibo.handy.data.repository.MatchingRepository
import com.unibo.handy.data.network.strategy.ChatMessageStrategy
import com.unibo.handy.data.network.strategy.ComputeMatchStrategy
import com.unibo.handy.data.network.strategy.MatchFoundStrategy
import com.unibo.handy.data.network.strategy.StoreProfileStrategy
import com.unibo.handy.domain.usecase.match.ComputeMatchUseCase
import com.unibo.handy.service.notifications.NotificationHelper
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
        computeMatchUseCase: ComputeMatchUseCase,
        notificationHelper: NotificationHelper,
        matchingRepository: MatchingRepository,
        chatRepository: ChatRepository,
        gson: Gson
    ): MessageDispatcher {
        val handlersMap = mapOf(
            "COMPUTE_MATCH" to ComputeMatchStrategy(computeMatchUseCase, matchingRepository, notificationHelper, gson),
            "STORE_PROFILE" to StoreProfileStrategy(matchingRepository, gson),
            "UPDATE_PROFILE" to StoreProfileStrategy(matchingRepository, gson),
            "CHAT_MESSAGE" to ChatMessageStrategy(chatRepository, gson),
            "MATCH_FOUND" to MatchFoundStrategy(matchingRepository, notificationHelper, gson)
        )
        return MessageDispatcher(handlersMap)
    }
}
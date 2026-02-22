package com.unibo.handy.data.repository.strategy

interface MessageStrategy {
    suspend fun handle(payload: String)
}
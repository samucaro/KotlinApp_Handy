package com.unibo.handy.data.network.strategy

interface MessageStrategy {
    suspend fun handle(payload: String)
}
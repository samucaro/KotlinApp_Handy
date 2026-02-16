package com.unibo.handy.data.repository.strategy

import com.google.gson.JsonElement

interface MessageStrategy {
    suspend fun handle(payload: JsonElement)
}
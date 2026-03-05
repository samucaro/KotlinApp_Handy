package com.unibo.handy.data.network.dto

import com.google.gson.annotations.SerializedName

data class ChatMessageDTO (
    @SerializedName("from") val from: String,
    @SerializedName("message") val message: String
)
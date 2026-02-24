package com.unibo.handy.data.network.dto

import com.google.gson.annotations.SerializedName

data class HeartBeatDTO(
    @SerializedName("clientId") val clientId: String,
    @SerializedName("blurredX") val blurredX: Long,
    @SerializedName("blurredY") val blurredY: Long,
    @SerializedName("encryptedBlur") val encryptedBlur: String
)

package com.unibo.handy.data.network.dto

import com.google.gson.annotations.SerializedName

data class HeartBeatDTO(
    @SerializedName("clientId") val clientId: String,

    @SerializedName("blurred_x") val blurredX: Long,
    @SerializedName("blurred_y") val blurredY: Long,
    @SerializedName("encrypted_blur") val encryptedBlur: Long
)

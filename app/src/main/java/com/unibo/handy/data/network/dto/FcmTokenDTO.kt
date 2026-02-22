package com.unibo.handy.data.network.dto

import com.google.gson.annotations.SerializedName

data class FcmTokenDTO(
    @SerializedName("clientId") val clientId: String,
    @SerializedName("fcmToken") val fcmToken: String
)

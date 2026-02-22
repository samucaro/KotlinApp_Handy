package com.unibo.handy.data.network.dto

import com.google.gson.annotations.SerializedName

data class RegistrationDTO(
    @SerializedName("clientId") val clientId: String,
    @SerializedName("category") val category: String,
    @SerializedName("isHelper") val isHelper: Boolean,
    @SerializedName("fcmToken") val fcmToken: String? = null
)

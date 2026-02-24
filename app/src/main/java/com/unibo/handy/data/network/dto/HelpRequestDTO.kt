package com.unibo.handy.data.network.dto

import com.google.gson.annotations.SerializedName

data class HelpRequestDTO(
    @SerializedName("clientId") val clientId: String,
    @SerializedName("category") val category: String,
    @SerializedName("blurredX") val blurredX: Long,
    @SerializedName("blurredY") val blurredY: Long,
    @SerializedName("encryptedR") val encryptedR: String,
    @SerializedName("encryptedTol") val encryptedTol: String,
    @SerializedName("publicModulus") val publicModulus: String
)

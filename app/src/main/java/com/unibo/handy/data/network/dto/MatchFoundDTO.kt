package com.unibo.handy.data.network.dto

import com.google.gson.annotations.SerializedName

data class MatchFoundDTO(
    @SerializedName("requester_id") val requesterId: String,
    @SerializedName("target_id") val targetId: String
)

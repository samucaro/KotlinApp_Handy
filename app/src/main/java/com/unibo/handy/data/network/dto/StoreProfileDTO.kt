package com.unibo.handy.data.network.dto

import com.google.gson.annotations.SerializedName

data class StoreProfileDTO(
    @SerializedName("target_id")
    val targetId: String,
    @SerializedName("reblurred_x")
    val reblurredX: Long,
    @SerializedName("reblurred_y")
    val reblurredY: Long,
    @SerializedName("username")
    val username: String,
    @SerializedName("category")
    val category: String,
    @SerializedName("rating")
    val rating: Int
)

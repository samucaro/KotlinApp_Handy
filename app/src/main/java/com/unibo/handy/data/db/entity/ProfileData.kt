package com.unibo.handy.data.db.entity

import androidx.room.ColumnInfo

data class ProfileData(
    @ColumnInfo("reblurred_x") val reblurredX: Long,
    @ColumnInfo("reblurred_y") val reblurredY: Long,
    @ColumnInfo(name = "username") val username: String,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo("rating") val rating: Int
)

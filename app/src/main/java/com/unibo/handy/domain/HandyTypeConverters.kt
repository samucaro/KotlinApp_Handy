package com.unibo.handy.domain

import androidx.room.TypeConverter
import com.unibo.handy.data.db.entity.MatchStatus

class HandyTypeConverters {
    @TypeConverter
    fun fromStatus(status: MatchStatus): String = status.name
    @TypeConverter
    fun toStatus(value: String): MatchStatus = MatchStatus.valueOf(value)
}
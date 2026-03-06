package com.unibo.handy.data.db.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stored_clients")
data class StoredClientEntity(
    @PrimaryKey val clientId: String,
    @Embedded val profile: ProfileData,
)

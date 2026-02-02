package com.unibo.handy.data.db.entity

import androidx.room.ColumnInfo

data class ProfileData(
    @ColumnInfo("reblurred_x") val reblurredX: Long,
    @ColumnInfo("reblurred_y") val reblurredY: Long,

    @ColumnInfo(name = "username") val username: String,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo("rating") val rating: Int,
    /*
     * Possibile aggiunta di una Map che mappa per ogni utante la recensione rilasciata per il
     * relativo client, questo però comportarebbe l'aggiunta di un Converter che dice al db come
     * salvare quest oggetto di tipo Map.
     */
)

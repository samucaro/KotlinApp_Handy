package com.unibo.handy.ui

import com.unibo.handy.data.db.entity.UserEntity

// serve a impacchettare tutti i dati che la UI deve mostrare in un unico oggetto rendendoli più
// comprensibili per la View.
// Sono sia dati provenienti dal db sia dati esclusivi della View
data class UserUiState(
    val currentUser: UserEntity? = null,
    val isHelperMode: Boolean = false,
    val statusMessage: String = "",
    val isLoading: Boolean = false,
    val selectedCategory: String = "Idraulico",
    val searchRadius: Float = 10f
)
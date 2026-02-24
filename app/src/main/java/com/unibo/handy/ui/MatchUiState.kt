package com.unibo.handy.ui

import com.unibo.handy.domain.model.User

data class MatchUiState(
    val currentUser: User? = null,
    val isHelperMode: Boolean = false,
    // Parametri per la ricerca
    val selectedCategory: String = "Generico",
    val toleranceRadius: Float = 10f,
    // Parametri Match
    val showMatchPopup: Boolean = false,
    val incomingMatchId: String? = null,
    val statusMessage: String = "Pronto"
)

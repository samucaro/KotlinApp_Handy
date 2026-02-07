package com.unibo.handy.ui.theme

import com.unibo.handy.data.db.entity.MatchEntity

// serve a impacchettare tutti i dati che la UI deve mostrare in un unico oggetto
data class HomeUiState(
    val userId: String = "",
    val username: String = "",
    val isHelperMode: Boolean = false,
    val selectedCategory: String = "",
    val toleranceRadius: Float = 0.0f, // Km
    val statusMessage: String = "",
    val showMatchSuccess: Boolean = false,
    val matchesList: List<MatchEntity> = emptyList()
)

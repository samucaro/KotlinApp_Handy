package com.unibo.handy.ui.features.match

import com.unibo.handy.domain.model.User

data class MatchUiState(
    val currentUser: User? = null,
    val isHelperMode: Boolean = false,
    val selectedCategory: String = "Generico",
    val toleranceRadius: Float = 10f,
    val showMatchPopup: Boolean = false,
    val incomingMatchId: String? = null,
    val incomingRequesterId: String? = null,
    val statusMessage: String = "Pronto"
)
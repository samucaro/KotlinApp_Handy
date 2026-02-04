package com.unibo.handy.ui.theme

data class HomeUiState(
    val userId: String = "Caricamento...",
    val isHelperMode: Boolean = false, // True = Offre aiuto, False = Cerca aiuto
    val selectedCategory: String = "Idraulico",
    val toleranceRadius: Float = 5.0f, // Km
    val statusMessage: String = "Pronto",
    val showMatchSuccess: Boolean = false
)

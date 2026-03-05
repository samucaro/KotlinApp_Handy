package com.unibo.handy.ui.features.auth

data class AuthUiState(
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val category: String = "Generico",
    val isLoading: Boolean = false,
    val isSignUpSuccess: Boolean = false,
    val error: String? = null
)
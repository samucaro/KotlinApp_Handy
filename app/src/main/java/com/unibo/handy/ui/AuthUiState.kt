package com.unibo.handy.ui

data class AuthUiState(
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val category: String = "Generic",
    val isLoading: Boolean = false,
    val isSignUpSuccess: Boolean = false,
    val error: String? = null
)

package com.unibo.handy.domain.model

data class User(
    val userId: String,
    val username: String,
    val email: String,
    val category: String,
    val helpModeActive: Boolean
)

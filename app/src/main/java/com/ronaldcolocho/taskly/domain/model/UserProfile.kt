package com.ronaldcolocho.taskly.domain.model

data class UserProfile(
    val uid: String,
    val displayName: String,
    val email: String = "",
    val emailLower: String = "",
    val phone: String,
    val photoURL: String = "",
    val createdAt: Long = 0L
)

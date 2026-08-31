package com.ronaldcolocho.taskly.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class UserProfileDto(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val emailLower: String = "",
    val phone: String = "",
    val photoURL: String = "",
    val createdAt: Long = 0L
)


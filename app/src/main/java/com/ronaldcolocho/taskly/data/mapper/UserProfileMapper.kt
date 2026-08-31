package com.ronaldcolocho.taskly.data.mapper

import com.ronaldcolocho.taskly.data.model.UserProfileDto
import com.ronaldcolocho.taskly.domain.model.UserProfile

fun UserProfileDto.toDomain(): UserProfile = UserProfile(
    uid = this.uid,
    displayName = this.displayName,
    email = this.email,
    emailLower = this.emailLower,
    phone = this.phone,
    photoURL = this.photoURL,
    createdAt = this.createdAt
)

fun UserProfile.toDto(): UserProfileDto = UserProfileDto(
    uid = this.uid,
    displayName = this.displayName,
    email = this.email,
    emailLower = this.emailLower,
    phone = this.phone,
    photoURL = this.photoURL,
    createdAt = this.createdAt
)

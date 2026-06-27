package com.ptmanager.backend.auth.dto

import com.ptmanager.backend.domain.User

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val user: User,
)

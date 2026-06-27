package com.ptmanager.backend.auth.dto

data class LogoutRequest(
    val deviceToken: String? = null,
)

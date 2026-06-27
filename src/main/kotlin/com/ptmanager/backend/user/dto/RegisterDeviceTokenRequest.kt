package com.ptmanager.backend.user.dto

import com.ptmanager.backend.domain.Platform
import jakarta.validation.constraints.NotBlank

data class RegisterDeviceTokenRequest(
    @field:NotBlank val token: String,
    val platform: Platform = Platform.ANDROID,
)

package com.ptmanager.backend.user.dto

import jakarta.validation.constraints.NotBlank

data class UpdateProfileRequest(
    @field:NotBlank val name: String,
)

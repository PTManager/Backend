package com.ptmanager.backend.shift.dto

import jakarta.validation.constraints.NotBlank

data class CheckInRequest(
    @field:NotBlank val qrToken: String,
)

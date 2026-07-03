package com.ptmanager.backend.handover.dto

import com.ptmanager.backend.domain.HandoverCategory
import jakarta.validation.constraints.NotBlank

data class CreateHandoverRequest(
    val workplaceId: Long,
    val category: HandoverCategory,
    @field:NotBlank val title: String,
    @field:NotBlank val content: String,
)

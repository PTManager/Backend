package com.ptmanager.backend.swaprequest.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** 대타 요청 생성. 요청자는 인증 토큰(principal)에서 결정한다. */
data class CreateSwapRequest(
    val shiftId: Long,
    @field:NotBlank @field:Size(max = 500) val reason: String,
)

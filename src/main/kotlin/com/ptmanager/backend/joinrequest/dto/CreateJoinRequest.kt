package com.ptmanager.backend.joinrequest.dto

import jakarta.validation.constraints.NotBlank

/** 매장 가입 신청. 신청자는 인증 토큰(principal)에서 결정한다. */
data class CreateJoinRequest(
    @field:NotBlank val inviteCode: String,
)

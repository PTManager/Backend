package com.ptmanager.backend.joinrequest.dto

import com.ptmanager.backend.domain.JoinRequestStatus
import java.time.Instant

/** 가입 신청 응답. 명세 JoinRequest 스키마대로 신청자 표시명을 포함한다. */
data class JoinRequestResponse(
    val id: Long?,
    val workplaceId: Long,
    val userId: Long,
    val userName: String?,
    val status: JoinRequestStatus,
    val createdAt: Instant?,
)

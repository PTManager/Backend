package com.ptmanager.backend.swaprequest.dto

import com.ptmanager.backend.domain.Shift
import com.ptmanager.backend.domain.SwapApplication
import com.ptmanager.backend.domain.SwapRequest

/** 대타 요청 상세 = 요청 + 대상 근무 + 지원자 목록. (API 명세의 SwapRequestDetail) */
data class SwapRequestDetail(
    val request: SwapRequest,
    val shift: Shift?,
    val applications: List<SwapApplication>,
)

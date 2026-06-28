package com.ptmanager.backend.workplace.dto

/** 매장 QR 출근 토큰. 직원 앱이 이 토큰을 스캔해 출근 체크에 사용한다. */
data class QrTokenResponse(
    val qrToken: String,
)

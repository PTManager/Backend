package com.ptmanager.backend.joinrequest.dto

/** 가입 신청 승인/거절 결정. (API 명세의 DecisionRequest) */
data class DecisionRequest(
    val decision: Decision,
) {
    enum class Decision { APPROVE, REJECT }
}

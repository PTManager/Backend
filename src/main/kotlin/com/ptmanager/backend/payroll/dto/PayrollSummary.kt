package com.ptmanager.backend.payroll.dto

/** 월 단위 인건비 집계. (API 명세의 PayrollSummary) */
data class PayrollSummary(
    val workplaceId: Long,
    val yearMonth: String,
    val totalAmount: Long,
    val items: List<PayrollItem>,
)

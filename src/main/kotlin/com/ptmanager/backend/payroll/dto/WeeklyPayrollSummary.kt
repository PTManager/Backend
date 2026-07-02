package com.ptmanager.backend.payroll.dto

/** 월을 4주 버킷(1–7, 8–14, 15–21, 22–말일)으로 나눈 주차별 인건비 집계. */
data class WeeklyPayrollSummary(
    val workplaceId: Long,
    val yearMonth: String,
    val weeks: List<WeeklyCost>,
) {

    data class WeeklyCost(
        val week: Int,
        val amount: Long,
    )
}

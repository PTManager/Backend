package com.ptmanager.backend.payroll.dto

/** 직원 본인의 월 급여 집계. (실근태 기준, 결근 제외) */
data class MyPayrollSummary(
    val yearMonth: String,
    val hourlyWage: Int,
    val workedMinutes: Long,
    val amount: Long,
    val weeks: List<WeeklyPayrollSummary.WeeklyCost> = emptyList(),
)

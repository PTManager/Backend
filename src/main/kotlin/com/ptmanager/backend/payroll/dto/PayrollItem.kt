package com.ptmanager.backend.payroll.dto

data class PayrollItem(
    val employeeId: Long,
    val employeeName: String,
    val hourlyWage: Int,
    val workedMinutes: Long,
    val amount: Long,
)

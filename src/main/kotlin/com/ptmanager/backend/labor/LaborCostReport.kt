package com.ptmanager.backend.labor

import java.time.LocalDate

data class LaborCostReport(
    val workplaceId: Long,
    val from: LocalDate,
    val to: LocalDate,
    val totalCost: Long,
    val employees: List<EmployeeCost>,
) {

    data class EmployeeCost(
        val employeeId: Long,
        val name: String,
        val totalMinutes: Long,
        val hourlyWage: Int,
        val cost: Long,
    )
}

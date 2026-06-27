package com.ptmanager.backend.payroll

import com.ptmanager.backend.payroll.dto.PayrollItem
import com.ptmanager.backend.payroll.dto.PayrollSummary
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.YearMonth

@RestController
@RequestMapping("/api/payroll")
class PayrollController(
    private val laborCostService: LaborCostService,
) {

    /** 월 단위 인건비 집계. yearMonth 예: 2026-06 */
    @GetMapping
    fun payroll(
        @RequestParam workplaceId: Long,
        @RequestParam yearMonth: String,
    ): PayrollSummary {
        val ym = YearMonth.parse(yearMonth)
        val report = laborCostService.calculate(workplaceId, ym.atDay(1), ym.atEndOfMonth())
        val items = report.employees.map {
            PayrollItem(
                employeeId = it.employeeId,
                employeeName = it.name,
                hourlyWage = it.hourlyWage,
                workedMinutes = it.totalMinutes,
                amount = it.cost,
            )
        }
        return PayrollSummary(workplaceId, yearMonth, report.totalCost, items)
    }
}

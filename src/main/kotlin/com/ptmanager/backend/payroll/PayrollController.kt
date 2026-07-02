package com.ptmanager.backend.payroll

import com.ptmanager.backend.payroll.dto.PayrollItem
import com.ptmanager.backend.payroll.dto.PayrollSummary
import com.ptmanager.backend.payroll.dto.WeeklyPayrollSummary
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.YearMonth
import java.time.format.DateTimeParseException

@RestController
@RequestMapping("/api/payroll")
class PayrollController(
    private val laborCostService: LaborCostService,
) {

    /** 월 단위 인건비 집계. yearMonth 예: 2026-06 */
    @GetMapping
    @PreAuthorize("hasRole('EMPLOYER')")
    fun payroll(
        @RequestParam workplaceId: Long,
        @RequestParam yearMonth: String,
    ): PayrollSummary {
        val ym = try {
            YearMonth.parse(yearMonth)
        } catch (ex: DateTimeParseException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "yearMonth 형식이 올바르지 않습니다 (YYYY-MM).")
        }
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

    /** 월 인건비의 주차별(1–7, 8–14, 15–21, 22–말일) 추이. yearMonth 예: 2026-06 */
    @GetMapping("/weekly")
    @PreAuthorize("hasRole('EMPLOYER')")
    fun weekly(
        @RequestParam workplaceId: Long,
        @RequestParam yearMonth: String,
    ): WeeklyPayrollSummary {
        val ym = try {
            YearMonth.parse(yearMonth)
        } catch (ex: DateTimeParseException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "yearMonth 형식이 올바르지 않습니다 (YYYY-MM).")
        }
        return WeeklyPayrollSummary(workplaceId, yearMonth, laborCostService.weeklyTotals(workplaceId, ym))
    }
}

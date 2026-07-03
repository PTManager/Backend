package com.ptmanager.backend.payroll

import com.ptmanager.backend.payroll.dto.MyPayrollSummary
import com.ptmanager.backend.payroll.dto.PayrollItem
import com.ptmanager.backend.payroll.dto.PayrollSummary
import com.ptmanager.backend.payroll.dto.WeeklyPayrollSummary
import com.ptmanager.backend.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
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
    private val userRepository: UserRepository,
) {

    /** 로그인한 직원 본인의 월 급여. yearMonth 예: 2026-06 */
    @GetMapping("/me")
    fun myPayroll(
        @AuthenticationPrincipal userId: Long,
        @RequestParam yearMonth: String,
    ): MyPayrollSummary {
        val ym = parseYearMonth(yearMonth)
        val user = userRepository.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.")
        }
        val workplaceId = user.workplaceId
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "소속 매장이 없습니다.")
        // calculate 는 매장 멤버면 접근 가능하므로 직원 본인도 호출 가능. 본인 항목만 추린다.
        val report = laborCostService.calculate(workplaceId, ym.atDay(1), ym.atEndOfMonth())
        val mine = report.employees.firstOrNull { it.employeeId == userId }
        return MyPayrollSummary(
            yearMonth = yearMonth,
            hourlyWage = mine?.hourlyWage ?: user.hourlyWage,
            workedMinutes = mine?.totalMinutes ?: 0,
            amount = mine?.cost ?: 0,
            weeks = laborCostService.weeklyTotals(workplaceId, ym, userId),
        )
    }

    /** 월 단위 인건비 집계. yearMonth 예: 2026-06 */
    @GetMapping
    @PreAuthorize("hasRole('EMPLOYER')")
    fun payroll(
        @RequestParam workplaceId: Long,
        @RequestParam yearMonth: String,
    ): PayrollSummary {
        val ym = parseYearMonth(yearMonth)
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
        val ym = parseYearMonth(yearMonth)
        return WeeklyPayrollSummary(workplaceId, yearMonth, laborCostService.weeklyTotals(workplaceId, ym))
    }

    private fun parseYearMonth(yearMonth: String): YearMonth = try {
        YearMonth.parse(yearMonth)
    } catch (ex: DateTimeParseException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "yearMonth 형식이 올바르지 않습니다 (YYYY-MM).")
    }
}

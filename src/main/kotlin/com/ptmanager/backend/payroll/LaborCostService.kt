package com.ptmanager.backend.payroll

import com.ptmanager.backend.common.access.WorkplaceAccessGuard
import com.ptmanager.backend.domain.AttendanceStatus
import com.ptmanager.backend.payroll.dto.LaborCostReport
import com.ptmanager.backend.payroll.dto.LaborCostReport.EmployeeCost
import com.ptmanager.backend.payroll.dto.WeeklyPayrollSummary.WeeklyCost
import com.ptmanager.backend.repository.ShiftRepository
import com.ptmanager.backend.repository.UserRepository
import com.ptmanager.backend.repository.WorkplaceRepository
import com.ptmanager.backend.domain.Shift
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.NoSuchElementException

@Service
class LaborCostService(
    private val workplaceRepository: WorkplaceRepository,
    private val shiftRepository: ShiftRepository,
    private val userRepository: UserRepository,
    private val accessGuard: WorkplaceAccessGuard,
) {

    fun calculate(workplaceId: Long, from: LocalDate, to: LocalDate): LaborCostReport {
        accessGuard.requireMemberOf(workplaceId)
        if (!workplaceRepository.existsById(workplaceId)) {
            throw NoSuchElementException("Workplace not found.")
        }
        require(!to.isBefore(from)) { "'to' must not be before 'from'." }

        val shifts = shiftRepository.findByWorkplaceIdAndWorkDateBetween(workplaceId, from, to)
            .filter { it.attendanceStatus != AttendanceStatus.ABSENT } // 결근은 집계 제외

        val minutesByEmployee = LinkedHashMap<Long, Long>()
        for (shift in shifts) {
            val minutes = actualOrScheduledMinutes(shift)
            minutesByEmployee.merge(shift.employeeId, minutes) { a, b -> a + b }
        }

        val users = userRepository.findAllById(minutesByEmployee.keys).associateBy { it.id }

        val employees = ArrayList<EmployeeCost>()
        var totalCost = 0L
        for ((employeeId, totalMinutes) in minutesByEmployee) {
            val user = users[employeeId]
            val hourlyWage = user?.hourlyWage ?: 0
            val name = user?.name ?: "(unknown)"
            val cost = totalMinutes * hourlyWage / 60
            totalCost += cost
            employees.add(EmployeeCost(employeeId, name, totalMinutes, hourlyWage, cost))
        }

        return LaborCostReport(workplaceId, from, to, totalCost, employees)
    }

    /**
     * 월을 4주 버킷(1–7, 8–14, 15–21, 22–말일)으로 나눠 주차별 인건비 합계를 낸다.
     * 근무는 한 번만 조회해 일자로 버킷에 분배한다. (calculate를 4번 부르지 않음)
     */
    fun weeklyTotals(workplaceId: Long, month: YearMonth): List<WeeklyCost> {
        accessGuard.requireMemberOf(workplaceId)
        if (!workplaceRepository.existsById(workplaceId)) {
            throw NoSuchElementException("Workplace not found.")
        }
        val shifts = shiftRepository
            .findByWorkplaceIdAndWorkDateBetween(workplaceId, month.atDay(1), month.atEndOfMonth())
            .filter { it.attendanceStatus != AttendanceStatus.ABSENT }
        val wages = userRepository.findAllById(shifts.map { it.employeeId }.distinct())
            .associate { it.id to it.hourlyWage }

        val buckets = LongArray(WEEK_BUCKETS)
        for (shift in shifts) {
            val index = ((shift.workDate.dayOfMonth - 1) / 7).coerceAtMost(WEEK_BUCKETS - 1)
            val wage = wages[shift.employeeId] ?: 0
            buckets[index] += actualOrScheduledMinutes(shift) * wage / 60
        }
        return buckets.mapIndexed { i, amount -> WeeklyCost(week = i + 1, amount = amount) }
    }

    /** 출퇴근 기록이 모두 있으면 실제 근무시간, 없으면 편성 시간으로 계산한다. */
    private fun actualOrScheduledMinutes(shift: Shift): Long {
        val inAt = shift.checkedInAt
        val outAt = shift.checkedOutAt
        return if (inAt != null && outAt != null && outAt.isAfter(inAt)) {
            Duration.between(inAt, outAt).toMinutes()
        } else {
            workedMinutes(shift.startTime, shift.endTime)
        }
    }

    /** 근무 시간(분). 야간 교대(end ≤ start)는 익일로 보정해 양수로 계산한다. */
    private fun workedMinutes(start: LocalTime, end: LocalTime): Long {
        val startMin = (start.toSecondOfDay() / 60).toLong()
        var endMin = (end.toSecondOfDay() / 60).toLong()
        if (endMin <= startMin) endMin += 24 * 60
        return endMin - startMin
    }

    companion object {
        private const val WEEK_BUCKETS = 4
    }
}

package com.ptmanager.backend.labor

import com.ptmanager.backend.labor.LaborCostReport.EmployeeCost
import com.ptmanager.backend.repository.ShiftRepository
import com.ptmanager.backend.repository.UserRepository
import com.ptmanager.backend.repository.WorkplaceRepository
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDate
import java.util.NoSuchElementException

@Service
class LaborCostService(
    private val workplaceRepository: WorkplaceRepository,
    private val shiftRepository: ShiftRepository,
    private val userRepository: UserRepository,
) {

    fun calculate(workplaceId: Long, from: LocalDate, to: LocalDate): LaborCostReport {
        if (!workplaceRepository.existsById(workplaceId)) {
            throw NoSuchElementException("Workplace not found.")
        }
        require(!to.isBefore(from)) { "'to' must not be before 'from'." }

        val shifts = shiftRepository.findByWorkplaceIdAndWorkDateBetween(workplaceId, from, to)

        val minutesByEmployee = LinkedHashMap<Long, Long>()
        for (shift in shifts) {
            val minutes = Duration.between(shift.startTime, shift.endTime).toMinutes()
            minutesByEmployee.merge(shift.employeeId, minutes) { a, b -> a + b }
        }

        val employees = ArrayList<EmployeeCost>()
        var totalCost = 0L
        for ((employeeId, totalMinutes) in minutesByEmployee) {
            val user = userRepository.findById(employeeId).orElse(null)
            val hourlyWage = user?.hourlyWage ?: 0
            val name = user?.name ?: "(unknown)"
            val cost = totalMinutes * hourlyWage / 60
            totalCost += cost
            employees.add(EmployeeCost(employeeId, name, totalMinutes, hourlyWage, cost))
        }

        return LaborCostReport(workplaceId, from, to, totalCost, employees)
    }
}

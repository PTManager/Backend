package com.ptmanager.backend.repository

import com.ptmanager.backend.domain.AttendanceStatus
import com.ptmanager.backend.domain.Shift
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface ShiftRepository : JpaRepository<Shift, Long> {

    fun findByEmployeeIdOrderByWorkDateAscStartTimeAsc(employeeId: Long): List<Shift>

    fun findByEmployeeIdAndWorkDate(employeeId: Long, workDate: LocalDate): List<Shift>

    fun findByAttendanceStatusAndWorkDateLessThanEqual(
        status: AttendanceStatus,
        workDate: LocalDate,
    ): List<Shift>

    fun findByWorkplaceIdOrderByWorkDateAscStartTimeAsc(workplaceId: Long): List<Shift>

    fun findByWorkplaceIdAndWorkDateBetween(workplaceId: Long, from: LocalDate, to: LocalDate): List<Shift>
}

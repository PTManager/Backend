package com.ptmanager.backend.shift

import com.ptmanager.backend.domain.AttendanceStatus
import com.ptmanager.backend.domain.Shift
import com.ptmanager.backend.repository.ShiftRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.NoSuchElementException

@Service
class ShiftService(
    private val shiftRepository: ShiftRepository,
) {

    fun findShifts(
        workplaceId: Long?,
        employeeId: Long?,
        from: LocalDate?,
        to: LocalDate?,
        status: AttendanceStatus?,
    ): List<Shift> {
        val base = when {
            employeeId != null ->
                shiftRepository.findByEmployeeIdOrderByWorkDateAscStartTimeAsc(employeeId)
            workplaceId != null && from != null && to != null ->
                shiftRepository.findByWorkplaceIdAndWorkDateBetween(workplaceId, from, to)
            workplaceId != null ->
                shiftRepository.findByWorkplaceIdOrderByWorkDateAscStartTimeAsc(workplaceId)
            else -> emptyList()
        }
        return if (status == null) base else base.filter { it.attendanceStatus == status }
    }

    fun getShift(id: Long): Shift =
        shiftRepository.findById(id)
            .orElseThrow { NoSuchElementException("Shift not found.") }

    @Transactional
    fun create(
        workplaceId: Long,
        employeeId: Long,
        workDate: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime,
    ): Shift =
        shiftRepository.save(
            Shift(
                workplaceId = workplaceId,
                employeeId = employeeId,
                workDate = workDate,
                startTime = startTime,
                endTime = endTime,
            ),
        )

    @Transactional
    fun update(
        id: Long,
        employeeId: Long?,
        workDate: LocalDate?,
        startTime: LocalTime?,
        endTime: LocalTime?,
    ): Shift {
        val shift = getShift(id)
        employeeId?.let { shift.employeeId = it }
        workDate?.let { shift.workDate = it }
        startTime?.let { shift.startTime = it }
        endTime?.let { shift.endTime = it }
        return shiftRepository.save(shift)
    }

    @Transactional
    fun delete(id: Long) {
        val shift = getShift(id)
        shiftRepository.delete(shift)
    }

    @Transactional
    fun checkIn(shiftId: Long): Shift {
        val shift = getShift(shiftId)
        require(shift.checkedInAt == null) { "Shift is already checked in." }

        val now = Instant.now()
        shift.checkedInAt = now
        // checked_in_at 과 start_time 비교로 PRESENT/LATE 판정 (결근 ABSENT는 배치가 담당)
        val scheduledStart = shift.workDate.atTime(shift.startTime)
            .atZone(ZoneId.systemDefault())
            .toInstant()
        shift.attendanceStatus =
            if (now.isAfter(scheduledStart)) AttendanceStatus.LATE else AttendanceStatus.PRESENT

        return shiftRepository.save(shift)
    }
}

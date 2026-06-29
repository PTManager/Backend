package com.ptmanager.backend.shift.dto

import com.ptmanager.backend.domain.AttendanceStatus
import com.ptmanager.backend.domain.Shift
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** 근무 응답. 명세 Shift 스키마대로 근무자 표시명(employeeName)을 포함한다. */
data class ShiftResponse(
    val id: Long?,
    val workplaceId: Long,
    val employeeId: Long,
    val employeeName: String?,
    val workDate: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val checkedInAt: Instant?,
    val attendanceStatus: AttendanceStatus,
    val createdAt: Instant?,
    val updatedAt: Instant?,
) {
    companion object {
        /** employeeName은 호출부가 조회해 넘긴다 (Shift에 없는 표시용 필드). */
        fun from(shift: Shift, employeeName: String?) = ShiftResponse(
            id = shift.id,
            workplaceId = shift.workplaceId,
            employeeId = shift.employeeId,
            employeeName = employeeName,
            workDate = shift.workDate,
            startTime = shift.startTime,
            endTime = shift.endTime,
            checkedInAt = shift.checkedInAt,
            attendanceStatus = shift.attendanceStatus,
            createdAt = shift.createdAt,
            updatedAt = shift.updatedAt,
        )
    }
}

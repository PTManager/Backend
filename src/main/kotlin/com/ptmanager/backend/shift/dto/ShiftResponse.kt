package com.ptmanager.backend.shift.dto

import com.ptmanager.backend.domain.AttendanceStatus
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
)

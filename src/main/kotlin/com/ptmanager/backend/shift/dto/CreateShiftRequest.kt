package com.ptmanager.backend.shift.dto

import java.time.LocalDate
import java.time.LocalTime

data class CreateShiftRequest(
    val workplaceId: Long,
    val employeeId: Long,
    val workDate: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
)

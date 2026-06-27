package com.ptmanager.backend.shift.dto

import java.time.LocalDate
import java.time.LocalTime

data class UpdateShiftRequest(
    val employeeId: Long? = null,
    val workDate: LocalDate? = null,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
)

package com.ptmanager.backend.shift

import com.ptmanager.backend.domain.AttendanceStatus
import com.ptmanager.backend.shift.dto.CheckInRequest
import com.ptmanager.backend.shift.dto.CreateShiftRequest
import com.ptmanager.backend.shift.dto.ShiftResponse
import com.ptmanager.backend.shift.dto.UpdateShiftRequest
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/shifts")
class ShiftController(
    private val shiftService: ShiftService,
) {

    @GetMapping
    fun findShifts(
        @AuthenticationPrincipal userId: Long,
        @RequestParam(required = false) workplaceId: Long?,
        @RequestParam(required = false) employeeId: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(required = false) status: AttendanceStatus?,
    ): List<ShiftResponse> {
        val resolvedEmployeeId = when (employeeId) {
            null -> null
            "me" -> userId
            else -> employeeId.toLong()
        }
        return shiftService.findShifts(workplaceId, resolvedEmployeeId, from, to, status)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('EMPLOYER')")
    fun createShift(@Valid @RequestBody request: CreateShiftRequest): ShiftResponse =
        shiftService.create(
            request.workplaceId,
            request.employeeId,
            request.workDate,
            request.startTime,
            request.endTime,
        )

    @GetMapping("/{shiftId}")
    fun getShift(@PathVariable shiftId: Long): ShiftResponse = shiftService.getShiftDetail(shiftId)

    @PatchMapping("/{shiftId}")
    @PreAuthorize("hasRole('EMPLOYER')")
    fun updateShift(
        @PathVariable shiftId: Long,
        @RequestBody request: UpdateShiftRequest,
    ): ShiftResponse = shiftService.update(
        shiftId,
        request.employeeId,
        request.workDate,
        request.startTime,
        request.endTime,
    )

    @DeleteMapping("/{shiftId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('EMPLOYER')")
    fun deleteShift(@PathVariable shiftId: Long) = shiftService.delete(shiftId)

    @PostMapping("/{shiftId}/check-in")
    fun checkIn(
        @AuthenticationPrincipal userId: Long,
        @PathVariable shiftId: Long,
        @Valid @RequestBody request: CheckInRequest,
    ): ShiftResponse = shiftService.checkIn(shiftId, userId, request.qrToken)

    @PostMapping("/{shiftId}/check-out")
    fun checkOut(
        @AuthenticationPrincipal userId: Long,
        @PathVariable shiftId: Long,
        @Valid @RequestBody request: CheckInRequest,
    ): ShiftResponse = shiftService.checkOut(shiftId, userId, request.qrToken)
}

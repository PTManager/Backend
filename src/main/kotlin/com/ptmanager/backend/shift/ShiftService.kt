package com.ptmanager.backend.shift

import com.ptmanager.backend.common.access.WorkplaceAccessGuard
import com.ptmanager.backend.domain.AttendanceStatus
import com.ptmanager.backend.domain.NotificationType
import com.ptmanager.backend.domain.Shift
import com.ptmanager.backend.notification.NotificationService
import com.ptmanager.backend.repository.ShiftRepository
import com.ptmanager.backend.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.NoSuchElementException

@Service
class ShiftService(
    private val shiftRepository: ShiftRepository,
    private val userRepository: UserRepository,
    private val notificationService: NotificationService,
    private val accessGuard: WorkplaceAccessGuard,
    private val qrCodeService: QrCodeService,
) {

    fun findShifts(
        workplaceId: Long?,
        employeeId: Long?,
        from: LocalDate?,
        to: LocalDate?,
        status: AttendanceStatus?,
    ): List<Shift> {
        if (workplaceId != null) {
            accessGuard.requireMemberOf(workplaceId)
        }
        if (employeeId != null && employeeId != accessGuard.currentUserId()) {
            val target = userRepository.findById(employeeId)
                .orElseThrow { NoSuchElementException("User not found.") }
            accessGuard.requireMemberOf(
                target.workplaceId
                    ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "해당 직원에 접근 권한이 없습니다."),
            )
        }
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

    fun getShift(id: Long): Shift {
        val shift = shiftRepository.findById(id)
            .orElseThrow { NoSuchElementException("Shift not found.") }
        accessGuard.requireMemberOf(shift.workplaceId)
        return shift
    }

    @Transactional
    fun create(
        workplaceId: Long,
        employeeId: Long,
        workDate: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime,
    ): Shift {
        accessGuard.requireMemberOf(workplaceId)
        val shift = shiftRepository.save(
            Shift(
                workplaceId = workplaceId,
                employeeId = employeeId,
                workDate = workDate,
                startTime = startTime,
                endTime = endTime,
            ),
        )
        notifyScheduleChanged(shift, "새 근무가 편성되었습니다.")
        return shift
    }

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
        val saved = shiftRepository.save(shift)
        notifyScheduleChanged(saved, "근무 편성이 변경되었습니다.")
        return saved
    }

    @Transactional
    fun delete(id: Long) {
        val shift = getShift(id)
        shiftRepository.delete(shift)
    }

    @Transactional
    fun checkIn(shiftId: Long, currentUserId: Long, qrToken: String): Shift {
        val shift = getShift(shiftId)
        if (shift.employeeId != currentUserId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "본인 근무만 출근 체크할 수 있습니다.")
        }
        qrCodeService.verify(shift.workplaceId, qrToken)
        require(shift.checkedInAt == null) { "이미 출근 처리된 근무입니다." }

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

    private fun notifyScheduleChanged(shift: Shift, message: String) {
        notificationService.notify(
            shift.employeeId,
            NotificationType.SCHEDULE_CHANGED,
            message,
            targetType = "SHIFT",
            targetId = shift.id,
        )
    }
}

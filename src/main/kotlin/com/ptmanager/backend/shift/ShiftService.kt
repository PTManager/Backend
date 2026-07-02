package com.ptmanager.backend.shift

import com.ptmanager.backend.common.access.WorkplaceAccessGuard
import com.ptmanager.backend.domain.AttendanceStatus
import com.ptmanager.backend.domain.NotificationType
import com.ptmanager.backend.domain.Shift
import com.ptmanager.backend.domain.SwapRequestStatus
import com.ptmanager.backend.notification.NotificationService
import com.ptmanager.backend.repository.ShiftRepository
import com.ptmanager.backend.repository.SwapApplicationRepository
import com.ptmanager.backend.repository.SwapRequestRepository
import com.ptmanager.backend.repository.UserRepository
import com.ptmanager.backend.shift.dto.ShiftResponse
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
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
    private val swapRequestRepository: SwapRequestRepository,
    private val swapApplicationRepository: SwapApplicationRepository,
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
    ): List<ShiftResponse> {
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
        val shifts = if (status == null) base else base.filter { it.attendanceStatus == status }

        val names = userRepository.findAllById(shifts.map { it.employeeId }.distinct())
            .associate { it.id to it.name }
        return shifts.map { toResponse(it, names[it.employeeId]) }
    }

    fun getShiftDetail(id: Long): ShiftResponse {
        val shift = getShift(id)
        return toResponse(shift, employeeNameOf(shift.employeeId))
    }

    @Transactional
    fun create(
        workplaceId: Long,
        employeeId: Long,
        workDate: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime,
    ): ShiftResponse {
        accessGuard.requireMemberOf(workplaceId)
        requireEmployeeInWorkplace(employeeId, workplaceId)
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
        return toResponse(shift, employeeNameOf(employeeId))
    }

    @Transactional
    fun update(
        id: Long,
        employeeId: Long?,
        workDate: LocalDate?,
        startTime: LocalTime?,
        endTime: LocalTime?,
    ): ShiftResponse {
        val shift = getShift(id)
        employeeId?.let {
            requireEmployeeInWorkplace(it, shift.workplaceId)
            shift.employeeId = it
        }
        workDate?.let { shift.workDate = it }
        startTime?.let { shift.startTime = it }
        endTime?.let { shift.endTime = it }
        val saved = shiftRepository.save(shift)
        notifyScheduleChanged(saved, "근무 편성이 변경되었습니다.")
        return toResponse(saved, employeeNameOf(saved.employeeId))
    }

    @Transactional
    fun delete(id: Long) {
        val shift = getShift(id)
        val shiftId = shift.id!!
        // 열린(PENDING) 대타 요청이 걸린 근무는 삭제할 수 없다. (ERD: ON DELETE RESTRICT)
        if (swapRequestRepository.existsByShiftIdAndStatus(shiftId, SwapRequestStatus.PENDING)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "대타 요청이 걸려 있어 삭제할 수 없습니다.")
        }
        // 이력(APPROVED/REJECTED) 대타 요청과 그 지원들을 함께 정리한다.
        val swaps = swapRequestRepository.findByShiftId(shiftId)
        val swapIds = swaps.mapNotNull { it.id }
        if (swapIds.isNotEmpty()) {
            swapApplicationRepository.deleteBySwapRequestIdIn(swapIds)
            swapRequestRepository.deleteAll(swaps)
        }
        shiftRepository.delete(shift)
    }

    @Transactional
    fun checkIn(shiftId: Long, currentUserId: Long, qrToken: String): ShiftResponse {
        val shift = getShift(shiftId)
        if (shift.employeeId != currentUserId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "본인 근무만 출근 체크할 수 있습니다.")
        }
        qrCodeService.verify(shift.workplaceId, qrToken)
        if (shift.checkedInAt != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이미 출근 처리된 근무입니다.")
        }

        val now = Instant.now()
        shift.checkedInAt = now
        // checked_in_at 과 start_time 비교로 PRESENT/LATE 판정 (결근 ABSENT는 배치가 담당)
        val scheduledStart = shift.workDate.atTime(shift.startTime)
            .atZone(ZoneId.systemDefault())
            .toInstant()
        shift.attendanceStatus =
            if (now.isAfter(scheduledStart)) AttendanceStatus.LATE else AttendanceStatus.PRESENT

        val saved = shiftRepository.save(shift)
        return toResponse(saved, employeeNameOf(saved.employeeId))
    }

    @Transactional
    fun checkOut(shiftId: Long, currentUserId: Long, qrToken: String): ShiftResponse {
        val shift = getShift(shiftId)
        if (shift.employeeId != currentUserId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "본인 근무만 퇴근 체크할 수 있습니다.")
        }
        if (shift.checkedInAt == null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "출근하지 않은 근무입니다.")
        }
        if (shift.checkedOutAt != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이미 퇴근 처리된 근무입니다.")
        }
        qrCodeService.verify(shift.workplaceId, qrToken)

        shift.checkedOutAt = Instant.now()
        val saved = shiftRepository.save(shift)
        return toResponse(saved, employeeNameOf(saved.employeeId))
    }

    /**
     * 근무 종료 시각이 지났는데도 체크인하지 않은(SCHEDULED) 근무를 결근(ABSENT) 처리한다.
     * ponytail: 매일 04시 1회. 실시간 결근 반영이 필요하면 주기를 줄이거나 조회 시점에 판정.
     */
    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    fun markAbsentees() {
        val now = Instant.now()
        val candidates = shiftRepository.findByAttendanceStatusAndWorkDateLessThanEqual(
            AttendanceStatus.SCHEDULED,
            LocalDate.now(),
        )
        for (shift in candidates) {
            if (now.isAfter(scheduledEndInstant(shift))) {
                shift.attendanceStatus = AttendanceStatus.ABSENT
            }
        }
        // JPA dirty checking이 트랜잭션 커밋 시 반영한다.
    }

    /** 야간 교대(end ≤ start)는 익일 종료로 보정. */
    private fun scheduledEndInstant(shift: Shift): Instant {
        val endDate =
            if (shift.endTime <= shift.startTime) shift.workDate.plusDays(1) else shift.workDate
        return endDate.atTime(shift.endTime).atZone(ZoneId.systemDefault()).toInstant()
    }

    private fun getShift(id: Long): Shift {
        val shift = shiftRepository.findById(id)
            .orElseThrow { NoSuchElementException("Shift not found.") }
        accessGuard.requireMemberOf(shift.workplaceId)
        return shift
    }

    private fun requireEmployeeInWorkplace(employeeId: Long, workplaceId: Long) {
        val employee = userRepository.findById(employeeId)
            .orElseThrow { ResponseStatusException(HttpStatus.BAD_REQUEST, "직원을 찾을 수 없습니다.") }
        if (employee.workplaceId != workplaceId) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "해당 매장 소속 직원이 아닙니다.")
        }
    }

    private fun employeeNameOf(employeeId: Long): String? =
        userRepository.findById(employeeId).orElse(null)?.name

    private fun notifyScheduleChanged(shift: Shift, message: String) {
        notificationService.notify(
            shift.employeeId,
            NotificationType.SCHEDULE_CHANGED,
            message,
            targetType = "SHIFT",
            targetId = shift.id,
        )
    }

    private fun toResponse(shift: Shift, employeeName: String?): ShiftResponse =
        ShiftResponse.from(shift, employeeName)
}

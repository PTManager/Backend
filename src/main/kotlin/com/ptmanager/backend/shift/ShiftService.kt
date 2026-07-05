package com.ptmanager.backend.shift

import com.ptmanager.backend.common.orNotFound

import com.ptmanager.backend.common.access.WorkplaceAccessGuard
import com.ptmanager.backend.domain.AttendanceStatus
import com.ptmanager.backend.domain.NotificationType
import com.ptmanager.backend.domain.Shift
import com.ptmanager.backend.domain.SwapRequestStatus
import com.ptmanager.backend.domain.UserRole
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
                .orNotFound("User not found.")
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
        // 날짜 범위는 모든 조회 경로에 일괄 적용한다. (employee 조회에서도 from/to가 반영되도록)
        val dateFiltered = base.filter {
            (from == null || !it.workDate.isBefore(from)) && (to == null || !it.workDate.isAfter(to))
        }
        val statusFiltered = if (status == null) dateFiltered else dateFiltered.filter { it.attendanceStatus == status }
        // 초안(미발행) 근무는 사장에게만 보인다. 직원 등 그 외에게는 발행된 것만 노출.
        val shifts = if (viewerIsEmployer()) statusFiltered else statusFiltered.filter { it.published }

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
        requireNoOverlap(employeeId, workDate, startTime, endTime, excludeShiftId = null)
        // 초안(published=false)으로 저장. 알림은 '발행' 시점에 한 번만 보낸다.
        val shift = shiftRepository.save(
            Shift(
                workplaceId = workplaceId,
                employeeId = employeeId,
                workDate = workDate,
                startTime = startTime,
                endTime = endTime,
                published = false,
            ),
        )
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
        requireNoOverlap(shift.employeeId, shift.workDate, shift.startTime, shift.endTime, excludeShiftId = shift.id)
        val saved = shiftRepository.save(shift)
        // 초안 수정은 조용히, 이미 발행된 근무를 바꾼 경우에만 변경 알림.
        if (saved.published) notifyScheduleChanged(saved, "근무 편성이 변경되었습니다.")
        return toResponse(saved, employeeNameOf(saved.employeeId))
    }

    /**
     * 지정 주(from~to)의 초안 근무를 발행한다. 발행된 직원마다 알림을 1건만 보내 도배를 막는다.
     * @return 발행된 근무 수
     */
    @Transactional
    fun publish(workplaceId: Long, from: LocalDate, to: LocalDate): Int {
        accessGuard.requireMemberOf(workplaceId)
        val drafts = shiftRepository.findByWorkplaceIdAndWorkDateBetween(workplaceId, from, to)
            .filter { !it.published }
        drafts.forEach { it.published = true }
        shiftRepository.saveAll(drafts)
        drafts.map { it.employeeId }.distinct().forEach { employeeId ->
            notificationService.notify(
                employeeId,
                NotificationType.SCHEDULE_CHANGED,
                "새 근무표가 발행되었습니다.",
                targetType = "SHIFT",
                targetId = null,
            )
        }
        return drafts.size
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
            .orNotFound("Shift not found.")
        accessGuard.requireMemberOf(shift.workplaceId)
        return shift
    }

    private fun viewerIsEmployer(): Boolean =
        userRepository.findById(accessGuard.currentUserId()).orElse(null)?.role == UserRole.EMPLOYER

    /**
     * 같은 직원이 같은 날 이미 편성된 근무와 시간이 겹치면 409.
     * ponytail: 같은 work_date 안에서만 검사한다. 자정 넘는 야간 교대(end<=start)는 비교에서 제외 —
     * 필요해지면 scheduledEndInstant처럼 익일 보정 후 Instant 비교로 확장.
     */
    private fun requireNoOverlap(
        employeeId: Long,
        workDate: LocalDate,
        start: LocalTime,
        end: LocalTime,
        excludeShiftId: Long?,
    ) {
        if (end <= start) return
        val conflict = shiftRepository.findByEmployeeIdAndWorkDate(employeeId, workDate)
            .filter { it.id != excludeShiftId && it.endTime > it.startTime }
            .any { it.startTime < end && start < it.endTime }
        if (conflict) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이미 편성된 근무와 시간이 겹칩니다.")
        }
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

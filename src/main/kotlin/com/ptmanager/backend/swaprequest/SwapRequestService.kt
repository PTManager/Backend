package com.ptmanager.backend.swaprequest

import com.ptmanager.backend.domain.NotificationType
import com.ptmanager.backend.domain.SwapApplication
import com.ptmanager.backend.domain.SwapRequest
import com.ptmanager.backend.domain.SwapRequestStatus
import com.ptmanager.backend.notification.NotificationService
import com.ptmanager.backend.repository.ShiftRepository
import com.ptmanager.backend.repository.SwapApplicationRepository
import com.ptmanager.backend.repository.SwapRequestRepository
import com.ptmanager.backend.swaprequest.dto.SwapRequestDetail
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.NoSuchElementException

@Service
class SwapRequestService(
    private val swapRequestRepository: SwapRequestRepository,
    private val swapApplicationRepository: SwapApplicationRepository,
    private val shiftRepository: ShiftRepository,
    private val notificationService: NotificationService,
) {

    @Transactional
    fun createSwapRequest(requesterId: Long, shiftId: Long, reason: String): SwapRequest {
        val shift = shiftRepository.findById(shiftId)
            .orElseThrow { NoSuchElementException("Shift not found.") }
        if (shift.employeeId != requesterId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "본인 근무만 대타 요청할 수 있습니다.")
        }
        val request = SwapRequest(
            workplaceId = shift.workplaceId,
            shiftId = shiftId,
            requesterId = requesterId,
            reason = reason,
            status = SwapRequestStatus.PENDING,
        )
        return swapRequestRepository.save(request)
    }

    /** view: open(지원 가능, 본인 제외) | mine(내 요청) | pending(승인 대기) */
    fun findSwapRequests(
        workplaceId: Long,
        view: String,
        currentUserId: Long,
        status: SwapRequestStatus?,
    ): List<SwapRequest> {
        val all = swapRequestRepository.findByWorkplaceIdOrderByCreatedAtDesc(workplaceId)
        return when (view) {
            "open" -> all.filter { it.status == SwapRequestStatus.PENDING && it.requesterId != currentUserId }
            "mine" -> all.filter { it.requesterId == currentUserId }
                .let { mine -> if (status == null) mine else mine.filter { it.status == status } }
            "pending" -> all.filter { it.status == SwapRequestStatus.PENDING }
            else -> all
        }
    }

    fun getDetail(id: Long): SwapRequestDetail {
        val request = swapRequestRepository.findById(id)
            .orElseThrow { NoSuchElementException("Swap request not found.") }
        val shift = shiftRepository.findById(request.shiftId).orElse(null)
        val applications = swapApplicationRepository.findBySwapRequestId(id)
        return SwapRequestDetail(request, shift, applications)
    }

    fun listApplications(swapRequestId: Long): List<SwapApplication> =
        swapApplicationRepository.findBySwapRequestId(swapRequestId)

    fun myApplications(applicantId: Long, status: SwapRequestStatus?): List<SwapApplication> {
        val all = swapApplicationRepository.findByApplicantIdOrderByCreatedAtDesc(applicantId)
        return if (status == null) all else all.filter { it.status == status }
    }

    @Transactional
    fun apply(swapRequestId: Long, applicantId: Long): SwapApplication {
        val request = swapRequestRepository.findById(swapRequestId)
            .orElseThrow { NoSuchElementException("Swap request not found.") }
        if (request.requesterId == applicantId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "본인 요청에는 지원할 수 없습니다.")
        }
        if (request.status != SwapRequestStatus.PENDING) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "마감된 대타 요청입니다.")
        }
        if (swapApplicationRepository.existsBySwapRequestIdAndApplicantId(swapRequestId, applicantId)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이미 지원한 요청입니다.")
        }
        // TODO: 더블부킹 검증 — 같은 work_date 에 시간이 겹치는 근무가 있으면 거부
        return swapApplicationRepository.save(
            SwapApplication(swapRequestId = swapRequestId, applicantId = applicantId),
        )
    }

    @Transactional
    fun approve(swapRequestId: Long, applicantId: Long): SwapRequest {
        val request = swapRequestRepository.findById(swapRequestId)
            .orElseThrow { NoSuchElementException("Swap request not found.") }
        if (request.status != SwapRequestStatus.PENDING) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이미 처리된 대타 요청입니다.")
        }

        request.status = SwapRequestStatus.APPROVED
        request.substituteId = applicantId
        val saved = swapRequestRepository.save(request)

        val applications = swapApplicationRepository.findBySwapRequestId(swapRequestId)
        applications.forEach {
            it.status = if (it.applicantId == applicantId) SwapRequestStatus.APPROVED else SwapRequestStatus.REJECTED
        }
        swapApplicationRepository.saveAll(applications)

        // 근무자를 대타자로 재배정 (requester_id 는 이력 보존을 위해 변경하지 않음)
        shiftRepository.findById(request.shiftId).ifPresent { shift ->
            shift.employeeId = applicantId
            shiftRepository.save(shift)
        }

        notificationService.notify(request.requesterId, NotificationType.SWAP_RESULT, "대타 요청이 승인되었습니다.")
        notificationService.notify(applicantId, NotificationType.SWAP_RESULT, "대타 지원이 승인되었습니다.")
        return saved
    }

    @Transactional
    fun reject(id: Long): SwapRequest {
        val request = swapRequestRepository.findById(id)
            .orElseThrow { NoSuchElementException("Swap request not found.") }
        if (request.status != SwapRequestStatus.PENDING) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이미 처리된 대타 요청입니다.")
        }
        request.status = SwapRequestStatus.REJECTED
        val saved = swapRequestRepository.save(request)
        notificationService.notify(request.requesterId, NotificationType.SWAP_RESULT, "대타 요청이 거절되었습니다.")
        return saved
    }
}

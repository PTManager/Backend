package com.ptmanager.backend.swap

import com.ptmanager.backend.domain.NotificationType
import com.ptmanager.backend.domain.SwapRequest
import com.ptmanager.backend.domain.SwapRequestStatus
import com.ptmanager.backend.notification.NotificationService
import com.ptmanager.backend.repository.SwapRequestRepository
import com.ptmanager.backend.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.NoSuchElementException

@Service
class SwapRequestService(
    private val swapRequestRepository: SwapRequestRepository,
    private val userRepository: UserRepository,
    private val notificationService: NotificationService,
) {

    fun findSwapRequests(): List<SwapRequest> = swapRequestRepository.findAllByOrderByCreatedAtDesc()

    @Transactional
    fun createSwapRequest(
        requesterId: Long,
        substituteId: Long?,
        workDate: LocalDate,
        reason: String,
    ): SwapRequest {
        val requester = userRepository.findById(requesterId)
            .orElseThrow { NoSuchElementException("Requester not found.") }
        if (substituteId != null && !userRepository.existsById(substituteId)) {
            throw NoSuchElementException("Substitute user not found.")
        }
        val request = SwapRequest(
            workplaceId = requester.workplaceId ?: 0,
            requesterId = requesterId,
            substituteId = substituteId,
            workDate = workDate,
            reason = reason,
            status = SwapRequestStatus.PENDING,
            createdAt = Instant.now(),
        )
        return swapRequestRepository.save(request)
    }

    @Transactional
    fun updateStatus(id: Long, status: SwapRequestStatus): SwapRequest {
        val request = swapRequestRepository.findById(id)
            .orElseThrow { NoSuchElementException("Swap request not found.") }
        request.status = status
        val saved = swapRequestRepository.save(request)
        notificationService.notify(
            saved.requesterId,
            NotificationType.SWAP_RESULT,
            "대타 요청이 ${status.name} 처리되었습니다.",
        )
        return saved
    }
}

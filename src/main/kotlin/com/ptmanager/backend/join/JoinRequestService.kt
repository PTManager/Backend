package com.ptmanager.backend.join

import com.ptmanager.backend.domain.JoinRequest
import com.ptmanager.backend.domain.JoinRequestStatus
import com.ptmanager.backend.domain.NotificationType
import com.ptmanager.backend.notification.NotificationService
import com.ptmanager.backend.repository.JoinRequestRepository
import com.ptmanager.backend.repository.UserRepository
import com.ptmanager.backend.repository.WorkplaceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.NoSuchElementException

@Service
class JoinRequestService(
    private val joinRequestRepository: JoinRequestRepository,
    private val workplaceRepository: WorkplaceRepository,
    private val userRepository: UserRepository,
    private val notificationService: NotificationService,
) {

    fun findByWorkplace(workplaceId: Long): List<JoinRequest> =
        joinRequestRepository.findByWorkplaceIdOrderByCreatedAtDesc(workplaceId)

    @Transactional
    fun create(inviteCode: String, userId: Long): JoinRequest {
        val workplace = workplaceRepository.findByInviteCode(inviteCode)
            ?: throw NoSuchElementException("Invalid invite code.")
        if (!userRepository.existsById(userId)) {
            throw NoSuchElementException("User not found.")
        }
        val request = JoinRequest(
            workplaceId = workplace.id!!,
            userId = userId,
            status = JoinRequestStatus.PENDING,
            createdAt = Instant.now(),
        )
        return joinRequestRepository.save(request)
    }

    @Transactional
    fun updateStatus(id: Long, status: JoinRequestStatus): JoinRequest {
        val request = joinRequestRepository.findById(id)
            .orElseThrow { NoSuchElementException("Join request not found.") }
        request.status = status
        val saved = joinRequestRepository.save(request)

        if (status == JoinRequestStatus.APPROVED) {
            val user = userRepository.findById(saved.userId)
                .orElseThrow { NoSuchElementException("User not found.") }
            user.workplaceId = saved.workplaceId
            userRepository.save(user)
        }
        notificationService.notify(
            saved.userId,
            NotificationType.JOIN_REQUEST,
            "매장 가입 신청이 ${status.name} 처리되었습니다.",
        )
        return saved
    }
}

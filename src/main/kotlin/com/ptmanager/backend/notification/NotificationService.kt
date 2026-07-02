package com.ptmanager.backend.notification

import com.ptmanager.backend.common.orNotFound
import com.ptmanager.backend.domain.Notification
import com.ptmanager.backend.domain.NotificationType
import com.ptmanager.backend.repository.NotificationRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.NoSuchElementException

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {

    fun findByUser(userId: Long, isRead: Boolean?, pageable: Pageable): Page<Notification> =
        if (isRead == null) {
            notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
        } else {
            notificationRepository.findByUserIdAndReadOrderByCreatedAtDesc(userId, isRead, pageable)
        }

    fun unreadCount(userId: Long): Long =
        notificationRepository.countByUserIdAndReadFalse(userId)

    @Transactional
    fun notify(
        userId: Long,
        type: NotificationType,
        message: String,
        targetType: String? = null,
        targetId: Long? = null,
    ): Notification {
        // 인앱 인박스에는 항상 적재한다.
        val notification = notificationRepository.save(
            Notification(
                userId = userId,
                type = type,
                message = message,
                targetType = targetType,
                targetId = targetId,
                read = false,
            ),
        )
        // FCM 푸시는 트랜잭션 커밋 후에만 나가도록 이벤트로 넘긴다. (NotificationPushListener)
        eventPublisher.publishEvent(PushRequested(listOf(userId), type, message, targetType, targetId))
        return notification
    }

    /** 여러 사용자에게 같은 알림을 발송한다. (대타 요청·공지 등 fan-out) */
    @Transactional
    fun notifyAll(
        userIds: List<Long>,
        type: NotificationType,
        message: String,
        targetType: String? = null,
        targetId: Long? = null,
    ) {
        if (userIds.isEmpty()) return
        // 인앱 알림은 한 번에 배치 저장하고, 푸시는 커밋 후 멀티캐스트 1회로 묶는다.
        notificationRepository.saveAll(
            userIds.map { userId ->
                Notification(
                    userId = userId,
                    type = type,
                    message = message,
                    targetType = targetType,
                    targetId = targetId,
                    read = false,
                )
            },
        )
        eventPublisher.publishEvent(PushRequested(userIds, type, message, targetType, targetId))
    }

    @Transactional
    fun markRead(id: Long, userId: Long) {
        val notification = notificationRepository.findById(id)
            .orNotFound("Notification not found.")
        // 본인 알림이 아니면 존재 자체를 숨긴다(정보 노출 방지).
        if (notification.userId != userId) {
            throw NoSuchElementException("Notification not found.")
        }
        notification.read = true
        notificationRepository.save(notification)
    }

    @Transactional
    fun markAllRead(userId: Long) {
        val unread = notificationRepository.findByUserIdAndRead(userId, false)
        unread.forEach { it.read = true }
        notificationRepository.saveAll(unread)
    }
}

package com.ptmanager.backend.notification

import com.ptmanager.backend.domain.Notification
import com.ptmanager.backend.domain.NotificationType
import com.ptmanager.backend.repository.NotificationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.NoSuchElementException

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
) {

    fun findByUser(userId: Long): List<Notification> =
        notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)

    @Transactional
    fun notify(userId: Long, type: NotificationType, message: String): Notification {
        val notification = Notification(
            userId = userId,
            type = type,
            message = message,
            read = false,
            createdAt = Instant.now(),
        )
        return notificationRepository.save(notification)
    }

    @Transactional
    fun markRead(id: Long): Notification {
        val notification = notificationRepository.findById(id)
            .orElseThrow { NoSuchElementException("Notification not found.") }
        notification.read = true
        return notificationRepository.save(notification)
    }
}

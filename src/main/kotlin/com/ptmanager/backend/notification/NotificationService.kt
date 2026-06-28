package com.ptmanager.backend.notification

import com.ptmanager.backend.common.push.PushSender
import com.ptmanager.backend.domain.Notification
import com.ptmanager.backend.domain.NotificationSetting
import com.ptmanager.backend.domain.NotificationType
import com.ptmanager.backend.repository.DeviceTokenRepository
import com.ptmanager.backend.repository.NotificationRepository
import com.ptmanager.backend.repository.NotificationSettingRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.NoSuchElementException

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val notificationSettingRepository: NotificationSettingRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val pushSender: PushSender,
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
        // FCM 푸시는 사용자의 알림 설정으로 게이팅한다.
        if (isPushEnabled(userId, type)) {
            val tokens = deviceTokenRepository.findByUserId(userId).map { it.token }
            pushSender.send(tokens, type, message, targetType, targetId)
        }
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
        userIds.forEach { notify(it, type, message, targetType, targetId) }
    }

    @Transactional
    fun markRead(id: Long, userId: Long) {
        val notification = notificationRepository.findById(id)
            .orElseThrow { NoSuchElementException("Notification not found.") }
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

    /** 알림 종류별로 사용자의 푸시 수신 설정을 확인한다. (설정 없으면 기본 ON) */
    private fun isPushEnabled(userId: Long, type: NotificationType): Boolean {
        val setting = notificationSettingRepository.findById(userId)
            .orElseGet { NotificationSetting(userId = userId) }
        return when (type) {
            NotificationType.SWAP_REQUEST,
            NotificationType.SWAP_APPLICATION,
            NotificationType.SWAP_RESULT,
            -> setting.swapEnabled
            NotificationType.NOTICE -> setting.noticeEnabled
            NotificationType.ATTENDANCE -> setting.attendanceEnabled
            NotificationType.JOIN_REQUEST -> setting.joinRequestEnabled
            NotificationType.SCHEDULE_CHANGED -> true
        }
    }
}

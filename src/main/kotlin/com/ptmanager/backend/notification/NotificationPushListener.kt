package com.ptmanager.backend.notification

import com.ptmanager.backend.common.push.PushSender
import com.ptmanager.backend.domain.NotificationSetting
import com.ptmanager.backend.domain.NotificationType
import com.ptmanager.backend.repository.DeviceTokenRepository
import com.ptmanager.backend.repository.NotificationSettingRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/** notify()/notifyAll()이 발행하는 푸시 요청. 알림 트랜잭션이 커밋된 뒤에만 실제 전송된다. */
data class PushRequested(
    val userIds: List<Long>,
    val type: NotificationType,
    val message: String,
    val targetType: String?,
    val targetId: Long?,
)

/**
 * 알림 커밋 이후(AFTER_COMMIT) FCM 푸시를 전송한다.
 * - 네트워크 호출이 DB 트랜잭션 밖에서 일어나 커넥션을 점유하지 않는다.
 * - 롤백된 트랜잭션의 푸시가 새어나가지 않는다.
 * - 다수 수신자는 설정 조회·토큰 조회를 배치로 한 뒤 멀티캐스트 1회로 발송한다.
 *
 * ponytail: 전송 실패는 stale 토큰 정리 외엔 무시(재시도 없음). 유실이 문제되면 아웃박스/재시도 큐로.
 */
@Component
class NotificationPushListener(
    private val notificationSettingRepository: NotificationSettingRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val pushSender: PushSender,
) {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onPushRequested(event: PushRequested) {
        val settings = notificationSettingRepository.findAllById(event.userIds)
            .associateBy { it.userId }
        val recipients = event.userIds.filter { isPushEnabled(settings[it], event.type) }
        if (recipients.isEmpty()) return

        val tokens = deviceTokenRepository.findByUserIdIn(recipients).map { it.token }
        if (tokens.isEmpty()) return

        pushSender.send(tokens, event.type, event.message, event.targetType, event.targetId)
    }

    /** 알림 종류별 푸시 수신 설정. 설정이 없으면 기본 ON. */
    private fun isPushEnabled(setting: NotificationSetting?, type: NotificationType): Boolean {
        val s = setting ?: NotificationSetting()
        return when (type) {
            NotificationType.SWAP_REQUEST,
            NotificationType.SWAP_APPLICATION,
            NotificationType.SWAP_RESULT,
            -> s.swapEnabled
            NotificationType.NOTICE -> s.noticeEnabled
            NotificationType.ATTENDANCE -> s.attendanceEnabled
            NotificationType.JOIN_REQUEST -> s.joinRequestEnabled
            NotificationType.SCHEDULE_CHANGED -> true
        }
    }
}

package com.ptmanager.backend.common.push

import com.ptmanager.backend.domain.NotificationType
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

/**
 * 기본(개발용) 푸시 발송기. 실제 전송 없이 로그만 남긴다.
 * `fcm.enabled=true` 가 아니면 이 빈이 사용된다. (실전송은 [FcmPushSender])
 */
@Service
@ConditionalOnProperty(prefix = "fcm", name = ["enabled"], havingValue = "false", matchIfMissing = true)
class LoggingPushSender : PushSender {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(
        deviceTokens: List<String>,
        type: NotificationType,
        message: String,
        targetType: String?,
        targetId: Long?,
    ) {
        if (deviceTokens.isEmpty()) return
        log.info(
            "[PUSH-STUB] {}개 토큰에 발송 — type={}, message='{}', target={}:{}",
            deviceTokens.size, type, message, targetType, targetId,
        )
    }
}

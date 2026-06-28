package com.ptmanager.backend.common.push

import com.ptmanager.backend.domain.NotificationType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 개발용 스텁 푸시 발송기. 실제 전송 없이 로그만 남긴다.
 * TODO: FcmPushSender(com.google.firebase:firebase-admin) 로 교체 — 멀티캐스트 발송.
 */
@Service
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

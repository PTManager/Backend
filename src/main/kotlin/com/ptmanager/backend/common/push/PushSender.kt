package com.ptmanager.backend.common.push

import com.ptmanager.backend.domain.NotificationType

/** FCM 푸시 발송 추상화. 운영에서는 Firebase Admin SDK 구현으로 교체한다. */
interface PushSender {

    fun send(
        deviceTokens: List<String>,
        type: NotificationType,
        message: String,
        targetType: String?,
        targetId: Long?,
    )
}

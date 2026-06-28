package com.ptmanager.backend.common.push

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification as FcmNotification
import com.ptmanager.backend.domain.NotificationType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.io.FileInputStream

/**
 * 실제 FCM 멀티캐스트 발송기. `fcm.enabled=true` 일 때만 활성화된다.
 * 서비스 계정 자격증명은 `fcm.credentials-path`(파일 경로) 또는
 * 환경변수 GOOGLE_APPLICATION_CREDENTIALS(애플리케이션 기본 자격증명)로 제공한다.
 */
@Service
@ConditionalOnProperty(prefix = "fcm", name = ["enabled"], havingValue = "true")
class FcmPushSender(
    @Value("\${fcm.credentials-path:}") credentialsPath: String,
) : PushSender {

    private val log = LoggerFactory.getLogger(javaClass)

    init {
        if (FirebaseApp.getApps().isEmpty()) {
            val credentials = if (credentialsPath.isNotBlank()) {
                FileInputStream(credentialsPath).use { GoogleCredentials.fromStream(it) }
            } else {
                GoogleCredentials.getApplicationDefault()
            }
            FirebaseApp.initializeApp(
                FirebaseOptions.builder().setCredentials(credentials).build(),
            )
            log.info("FirebaseApp 초기화 완료 (FCM 실전송 활성화)")
        }
    }

    override fun send(
        deviceTokens: List<String>,
        type: NotificationType,
        message: String,
        targetType: String?,
        targetId: Long?,
    ) {
        if (deviceTokens.isEmpty()) return

        val data = buildMap {
            put("type", type.name)
            targetType?.let { put("targetType", it) }
            targetId?.let { put("targetId", it.toString()) }
        }
        val multicast = MulticastMessage.builder()
            .addAllTokens(deviceTokens)
            .setNotification(FcmNotification.builder().setTitle("PTManager").setBody(message).build())
            .putAllData(data)
            .build()

        val response = FirebaseMessaging.getInstance().sendEachForMulticast(multicast)
        if (response.failureCount > 0) {
            log.warn("FCM 발송 일부 실패: {}/{} 성공", response.successCount, deviceTokens.size)
        }
    }
}

package com.ptmanager.backend.common.push

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification as FcmNotification
import com.ptmanager.backend.domain.NotificationType
import com.ptmanager.backend.repository.DeviceTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.io.FileInputStream

/**
 * 실제 FCM 멀티캐스트 발송기. `fcm.enabled=true` 일 때만 활성화된다.
 * 서비스 계정 자격증명은 `fcm.credentials-path`(파일 경로) 또는
 * 환경변수 GOOGLE_APPLICATION_CREDENTIALS(애플리케이션 기본 자격증명)로 제공한다.
 * 발송 결과에서 무효(UNREGISTERED/INVALID_ARGUMENT) 토큰은 DB에서 자동 정리한다.
 */
@Service
@ConditionalOnProperty(prefix = "fcm", name = ["enabled"], havingValue = "true")
class FcmPushSender(
    @Value("\${fcm.credentials-path:}") credentialsPath: String,
    private val deviceTokenRepository: DeviceTokenRepository,
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
        if (response.failureCount == 0) return

        // 더 이상 유효하지 않은 토큰은 DB에서 제거한다.
        val staleTokens = response.responses.mapIndexedNotNull { index, sendResponse ->
            val errorCode = sendResponse.exception?.messagingErrorCode
            if (!sendResponse.isSuccessful &&
                (errorCode == MessagingErrorCode.UNREGISTERED || errorCode == MessagingErrorCode.INVALID_ARGUMENT)
            ) {
                deviceTokens[index]
            } else {
                null
            }
        }
        if (staleTokens.isNotEmpty()) {
            deviceTokenRepository.deleteByTokenIn(staleTokens)
            log.info("무효 FCM 토큰 {}건 정리", staleTokens.size)
        }
        log.warn("FCM 발송 일부 실패: {}/{} 성공", response.successCount, deviceTokens.size)
    }
}

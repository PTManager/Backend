package com.ptmanager.backend.shift

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 매장 QR 출근 토큰 발급·검증. 형식 `wp{workplaceId}:{epochSeconds}:{signatureHex}`.
 * signature = HMAC-SHA256(secret, "wp{workplaceId}:{epochSeconds}").
 */
@Component
class QrCodeService(
    @Value("\${qr.secret}") secret: String,
    @Value("\${qr.max-age-seconds:0}") private val maxAgeSeconds: Long,
) {

    private val keySpec = SecretKeySpec(secret.toByteArray(), HMAC_ALGORITHM)

    /** 해당 매장의 현재 QR 토큰을 발급한다. (사장 앱이 매장에 게시할 QR 생성용) */
    fun issue(workplaceId: Long): String {
        val payload = "wp$workplaceId:${Instant.now().epochSecond}"
        return "$payload:${sign(payload)}"
    }

    /** 토큰이 해당 매장의 유효한 서명인지 검증한다. 실패 시 IllegalArgumentException(→400). */
    fun verify(workplaceId: Long, token: String) {
        val parts = token.split(":")
        require(parts.size == 3) { "유효하지 않은 QR 토큰입니다." }
        val (workplacePart, timestampPart, signature) = parts

        require(workplacePart == "wp$workplaceId") { "다른 매장의 QR 코드입니다." }

        val payload = "$workplacePart:$timestampPart"
        require(constantTimeEquals(sign(payload), signature)) { "QR 서명이 유효하지 않습니다." }

        if (maxAgeSeconds > 0) {
            val issuedAt = timestampPart.toLongOrNull()
                ?: throw IllegalArgumentException("유효하지 않은 QR 토큰입니다.")
            val age = Instant.now().epochSecond - issuedAt
            require(age in 0..maxAgeSeconds) { "만료된 QR 코드입니다." }
        }
    }

    private fun sign(payload: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(keySpec)
        return mac.doFinal(payload.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun constantTimeEquals(expected: String, actual: String): Boolean =
        MessageDigest.isEqual(expected.toByteArray(), actual.toByteArray())

    companion object {
        private const val HMAC_ALGORITHM = "HmacSHA256"
    }
}

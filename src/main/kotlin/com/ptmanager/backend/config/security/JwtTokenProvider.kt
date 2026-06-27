package com.ptmanager.backend.config.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

/**
 * JWT 액세스/리프레시 토큰 발급·검증. HS256 대칭키 서명.
 * 토큰 subject = userId, 액세스 토큰에는 role 클레임 포함.
 */
@Component
class JwtTokenProvider(
    @Value("\${jwt.secret}") secret: String,
    @Value("\${jwt.access-token-expiration-ms}") private val accessTokenExpirationMs: Long,
    @Value("\${jwt.refresh-token-expiration-ms}") private val refreshTokenExpirationMs: Long,
) {

    private val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray())

    val accessTokenExpirySeconds: Long
        get() = accessTokenExpirationMs / 1000

    fun createAccessToken(userId: Long, role: String): String =
        buildToken(userId, role, accessTokenExpirationMs)

    fun createRefreshToken(userId: Long): String =
        buildToken(userId, null, refreshTokenExpirationMs)

    fun parseUserId(token: String): Long = parse(token).subject.toLong()

    fun parseRole(token: String): String? = parse(token)["role"] as String?

    fun validate(token: String): Boolean =
        try {
            parse(token)
            true
        } catch (ex: JwtException) {
            false
        } catch (ex: IllegalArgumentException) {
            false
        }

    private fun buildToken(userId: Long, role: String?, expirationMs: Long): String {
        val now = Date()
        val builder = Jwts.builder()
            .subject(userId.toString())
            .issuedAt(now)
            .expiration(Date(now.time + expirationMs))
            .signWith(key)
        if (role != null) {
            builder.claim("role", role)
        }
        return builder.compact()
    }

    private fun parse(token: String): Claims =
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
}

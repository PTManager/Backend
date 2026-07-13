package com.ptmanager.backend.auth

import com.ptmanager.backend.common.orNotFound

import com.ptmanager.backend.auth.dto.TokenResponse
import com.ptmanager.backend.config.security.JwtTokenProvider
import com.ptmanager.backend.domain.User
import com.ptmanager.backend.domain.UserRole
import com.ptmanager.backend.repository.DeviceTokenRepository
import com.ptmanager.backend.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider,
) {

    @Transactional
    fun signup(email: String, password: String, name: String, role: UserRole): TokenResponse {
        if (userRepository.findByEmailIgnoreCase(email) != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다.")
        }
        val user = userRepository.save(
            User(
                email = email,
                password = passwordEncoder.encode(password),
                name = name,
                role = role,
            ),
        )
        return issueTokens(user)
    }

    fun login(email: String, password: String): TokenResponse {
        val user = userRepository.findByEmailIgnoreCase(email)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 일치하지 않습니다.")
        if (!passwordEncoder.matches(password, user.password)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 일치하지 않습니다.")
        }
        return issueTokens(user)
    }

    fun refresh(refreshToken: String): TokenResponse {
        if (!jwtTokenProvider.validate(refreshToken)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 리프레시 토큰입니다.")
        }
        val userId = jwtTokenProvider.parseUserId(refreshToken)
        val user = userRepository.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다.") }
        return issueTokens(user)
    }

    fun getMe(userId: Long): User =
        userRepository.findById(userId)
            .orNotFound("User not found.")

    /** 현재 기기의 FCM 토큰을 제거한다. (스테이트리스 JWT라 액세스 토큰 자체는 만료까지 유효) */
    @Transactional
    fun logout(userId: Long, deviceToken: String?) {
        if (deviceToken == null) return
        deviceTokenRepository.findByToken(deviceToken)?.let {
            if (it.userId == userId) deviceTokenRepository.delete(it)
        }
    }

    private fun issueTokens(user: User): TokenResponse {
        val id = user.id!!
        return TokenResponse(
            accessToken = jwtTokenProvider.createAccessToken(id, user.role.name),
            refreshToken = jwtTokenProvider.createRefreshToken(id),
            expiresIn = jwtTokenProvider.accessTokenExpirySeconds,
            user = user,
        )
    }
}

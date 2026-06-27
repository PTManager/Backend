package com.ptmanager.backend.auth

import com.ptmanager.backend.auth.dto.LoginRequest
import com.ptmanager.backend.auth.dto.LogoutRequest
import com.ptmanager.backend.auth.dto.RefreshRequest
import com.ptmanager.backend.auth.dto.SignupRequest
import com.ptmanager.backend.auth.dto.TokenResponse
import com.ptmanager.backend.domain.User
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
) {

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    fun signup(@Valid @RequestBody request: SignupRequest): TokenResponse =
        authService.signup(request.email, request.password, request.name, request.role)

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): TokenResponse =
        authService.login(request.email, request.password)

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): TokenResponse =
        authService.refresh(request.refreshToken)

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@RequestBody(required = false) request: LogoutRequest?) {
        // 스테이트리스 JWT — 클라이언트가 토큰을 폐기한다.
        // (추후 device_token 연동 시 request.deviceToken 제거 로직 추가)
    }

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal userId: Long): User = authService.getMe(userId)
}

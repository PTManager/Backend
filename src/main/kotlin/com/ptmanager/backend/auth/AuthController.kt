package com.ptmanager.backend.auth

import com.ptmanager.backend.domain.User
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
) {

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): LoginResponse {
        val user = authService.login(request.email, request.password)
        return LoginResponse("dev-token-" + user.id, user)
    }

    data class LoginRequest(
        @field:Email @field:NotBlank val email: String,
        @field:NotBlank val password: String,
    )

    data class LoginResponse(
        val accessToken: String,
        val user: User,
    )
}

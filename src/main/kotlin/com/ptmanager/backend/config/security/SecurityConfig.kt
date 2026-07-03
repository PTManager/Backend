package com.ptmanager.backend.config.security

import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val jwtTokenProvider: JwtTokenProvider,
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { }
            .headers { headers -> headers.frameOptions { it.sameOrigin() } } // H2 콘솔
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { authorize ->
                authorize
                    .requestMatchers(*PUBLIC_PATHS).permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling { exception ->
                exception.authenticationEntryPoint(
                    AuthenticationEntryPoint { _, response, _ ->
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
                    },
                )
            }
            .addFilterBefore(
                JwtAuthenticationFilter(jwtTokenProvider),
                UsernamePasswordAuthenticationFilter::class.java,
            )
        return http.build()
    }

    companion object {
        private val PUBLIC_PATHS = arrayOf(
            // 예외 발생 시 스프링이 포워딩하는 에러 디스패치 경로. 열어두지 않으면
            // 내부 500 이 인증 필터에 걸려 '빈 401'로 둔갑해 진짜 원인이 가려진다.
            "/error",
            "/api/auth/signup",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/health",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/h2-console/**",
        )
    }
}

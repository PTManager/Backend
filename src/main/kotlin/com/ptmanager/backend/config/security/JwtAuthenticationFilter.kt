package com.ptmanager.backend.config.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Authorization: Bearer {token} 헤더를 파싱해 유효하면 SecurityContext에 인증을 주입한다.
 * principal = userId(Long), authority = ROLE_{role}.
 * (SecurityConfig에서 직접 생성해 등록 — @Component로 두면 서블릿 필터로 중복 등록되므로 사용하지 않음)
 */
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            val token = header.substring(BEARER_PREFIX.length)
            if (jwtTokenProvider.validate(token)) {
                val userId = jwtTokenProvider.parseUserId(token)
                val role = jwtTokenProvider.parseRole(token)
                val authorities = role?.let { listOf(SimpleGrantedAuthority("ROLE_$it")) } ?: emptyList()
                val authentication = UsernamePasswordAuthenticationToken(userId, null, authorities)
                // Spring Security 6 권장: 컨텍스트를 '수정'하지 말고 새로 만들어 '교체'한다.
                // (deferred SecurityContext를 mutate 하면 이후 인가 단계에서 인증이 유실될 수 있음)
                val context = SecurityContextHolder.createEmptyContext()
                context.authentication = authentication
                SecurityContextHolder.setContext(context)
            }
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        private const val BEARER_PREFIX = "Bearer "
    }
}

package com.ptmanager.backend.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun ptManagerOpenAPI(): OpenAPI {
        val bearerScheme = "bearerAuth"
        return OpenAPI()
            .info(
                Info()
                    .title("PTManager API")
                    .version("1.0.0")
                    .description(
                        "알바생·사장님을 연결하는 근무 스케줄·대타·근태 관리 앱 PTManager의 REST API. " +
                            "인증은 JWT(Bearer), 역할(EMPLOYEE/EMPLOYER) 기반 RBAC.",
                    )
                    .contact(Contact().name("PTManager Team").email("team@ptmanager.app")),
            )
            .servers(
                listOf(
                    Server().url("http://localhost:8080").description("로컬 개발 서버"),
                    Server().url("https://api.ptmanager.app").description("운영 서버 (AWS EC2 + RDS)"),
                ),
            )
            .addSecurityItem(SecurityRequirement().addList(bearerScheme))
            .components(
                Components().addSecuritySchemes(
                    bearerScheme,
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("로그인 후 발급받은 액세스 토큰을 Authorization: Bearer {token} 헤더로 전달"),
                ),
            )
    }
}

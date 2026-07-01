package com.ptmanager.backend.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.OpenApiCustomizer
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
            // 서버 URL은 하드코딩하지 않는다 → SpringDoc이 요청 호스트 기준으로 자동 설정.
            // (로컬·EC2 어디서 열든 Swagger "Try it out"이 그 호스트로 요청을 보냄)
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

    /**
     * 공통 에러 응답을 라이브 Swagger 문서에 반영한다.
     * - `ApiError` 스키마 등록
     * - 모든 엔드포인트에 공통 에러(400/401/403/404/409)를 일괄 추가
     *   (이미 선언된 코드는 덮어쓰지 않음)
     */
    @Bean
    fun commonErrorResponsesCustomizer(): OpenApiCustomizer = OpenApiCustomizer { openApi ->
        if (openApi.components == null) {
            openApi.components = Components()
        }
        openApi.components.addSchemas("ApiError", apiErrorSchema())

        val commonErrors = linkedMapOf(
            "400" to "잘못된 요청 / 검증 실패 (VALIDATION_FAILED·BAD_REQUEST)",
            "401" to "인증 필요 또는 토큰 만료",
            "403" to "권한 없음 (역할·소속 매장 불일치)",
            "404" to "리소스를 찾을 수 없음",
            "409" to "충돌 (중복·이미 처리됨 등)",
        )
        openApi.paths?.values?.forEach { pathItem ->
            pathItem.readOperations().forEach { operation ->
                commonErrors.forEach { (code, description) ->
                    if (!operation.responses.containsKey(code)) {
                        operation.responses.addApiResponse(
                            code,
                            ApiResponse().description(description).content(apiErrorContent()),
                        )
                    }
                }
            }
        }
    }

    /**
     * QR 출근 체크(`checkIn`)의 400 응답에 QrCodeService.verify()가 실제로 던지는
     * 사유별 메시지를 예시로 붙인다. (형식·매장불일치·서명·만료·재사용)
     */
    @Bean
    fun checkInQrErrorExamplesCustomizer(): OpenApiCustomizer = OpenApiCustomizer { openApi ->
        val operation = openApi.paths?.values
            ?.flatMap { it.readOperations() }
            ?.firstOrNull { it.operationId == "checkIn" }
            ?: return@OpenApiCustomizer

        val examples = linkedMapOf(
            "만료된 QR" to "만료된 QR 코드입니다.",
            "이미 갱신된 QR (재사용)" to "이미 갱신된 QR 코드입니다. 최신 QR로 다시 스캔해 주세요.",
            "다른 매장의 QR" to "다른 매장의 QR 코드입니다.",
            "서명 위조" to "QR 서명이 유효하지 않습니다.",
            "형식 오류" to "유효하지 않은 QR 토큰입니다.",
        )
        val content = Content().addMediaType(
            "application/json",
            MediaType()
                .schema(Schema<Any>().`$ref`("#/components/schemas/ApiError"))
                .apply {
                    examples.forEach { (name, message) ->
                        addExamples(
                            name,
                            Example().value(
                                mapOf(
                                    "code" to "BAD_REQUEST",
                                    "message" to message,
                                    "timestamp" to "2026-07-01T06:20:23.310Z",
                                    "fields" to emptyMap<String, String>(),
                                ),
                            ),
                        )
                    }
                },
        )
        operation.responses.addApiResponse(
            "400",
            ApiResponse().description("QR 검증 실패 (QrCodeService.verify) — 사유별 예시 참고").content(content),
        )
    }

    private fun apiErrorSchema(): Schema<*> =
        ObjectSchema()
            .description("공통 에러 응답 (ApiExceptionHandler)")
            .addProperty("code", StringSchema().example("CONFLICT"))
            .addProperty("message", StringSchema().example("이미 처리된 요청입니다."))
            .addProperty("timestamp", StringSchema().format("date-time"))
            .addProperty(
                "fields",
                ObjectSchema()
                    .additionalProperties(StringSchema())
                    .description("필드별 검증 오류 메시지 (VALIDATION_FAILED 시)"),
            )

    private fun apiErrorContent(): Content =
        Content().addMediaType(
            "application/json",
            MediaType().schema(Schema<Any>().`$ref`("#/components/schemas/ApiError")),
        )
}

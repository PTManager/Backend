package com.ptmanager.backend.api

import com.jayway.jsonpath.JsonPath
import com.ptmanager.backend.shift.QrCodeService
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class BaselineApiTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var qrCodeService: QrCodeService

    private fun loginAs(email: String): String {
        val response = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"password"}"""),
        )
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
        return JsonPath.read(response, "$.accessToken")
    }

    @Test
    fun healthReturnsUp() {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status", `is`("UP")))
    }

    @Test
    fun openApiDocIsServed() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.info.title", `is`("PTManager API")))
    }

    @Test
    fun loginReturnsTokensAndSeedEmployee() {
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"employee@ptmanager.test","password":"password"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists())
            .andExpect(jsonPath("$.user.role", `is`("EMPLOYEE")))
            .andExpect(jsonPath("$.user.password").doesNotExist())
    }

    @Test
    fun loginWithWrongPasswordReturnsUnauthorized() {
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"employee@ptmanager.test","password":"wrong"}"""),
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun meReturnsCurrentUser() {
        val token = loginAs("employee@ptmanager.test")
        mockMvc.perform(
            get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email", `is`("employee@ptmanager.test")))
    }

    @Test
    fun protectedEndpointWithoutTokenIsUnauthorized() {
        mockMvc.perform(get("/api/swap-requests"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun employeeForbiddenFromEmployerEndpoint() {
        val token = loginAs("employee@ptmanager.test")
        mockMvc.perform(
            post("/api/shifts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "workplaceId": 1,
                      "employeeId": 1,
                      "workDate": "2026-07-01",
                      "startTime": "09:00:00",
                      "endTime": "14:00:00"
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun cannotAccessOtherWorkplaceData() {
        val token = loginAs("employee@ptmanager.test") // 소속 매장 = 1
        mockMvc.perform(
            get("/api/notices?workplaceId=999").header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun canAccessOwnWorkplaceData() {
        val token = loginAs("employee@ptmanager.test")
        mockMvc.perform(
            get("/api/notices?workplaceId=1").header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun employerCanCreateWorkplace() {
        val token = loginAs("employer@ptmanager.test")
        mockMvc.perform(
            post("/api/workplaces")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"테스트 매장","address":"서울"}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.inviteCode").exists())
    }

    @Test
    fun noticeResponseIncludesAuthorNameAndAttachments() {
        // 새 사장 가입 → 매장 생성(자동 소속) → 첨부 포함 공지 작성 → 응답 검증
        val signup = mockMvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"noticeboss@ptmanager.test","password":"password1","name":"공지사장","role":"EMPLOYER"}"""),
        )
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString
        val token: String = JsonPath.read(signup, "$.accessToken")

        val workplace = mockMvc.perform(
            post("/api/workplaces")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"공지 테스트 매장"}"""),
        )
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString
        val workplaceId: Int = JsonPath.read(workplace, "$.id")

        mockMvc.perform(
            post("/api/notices")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "workplaceId": $workplaceId,
                      "title": "안내",
                      "body": "본문",
                      "attachmentUrls": ["https://example.com/a.jpg"]
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.authorName", `is`("공지사장")))
            .andExpect(jsonPath("$.attachments[0].fileUrl", `is`("https://example.com/a.jpg")))
    }

    @Test
    fun notificationInboxUsesIsReadAndIsOwnerScoped() {
        // 사장 E 가입 + 매장 생성
        val eSignup = mockMvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"notif-boss@ptmanager.test","password":"password1","name":"알림사장","role":"EMPLOYER"}"""),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val eToken: String = JsonPath.read(eSignup, "$.accessToken")

        val workplace = mockMvc.perform(
            post("/api/workplaces")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $eToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"알림 테스트 매장"}"""),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val inviteCode: String = JsonPath.read(workplace, "$.inviteCode")

        // 직원 M 가입 + 가입 신청
        val mSignup = mockMvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"notif-emp@ptmanager.test","password":"password1","name":"알림직원","role":"EMPLOYEE"}"""),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val mToken: String = JsonPath.read(mSignup, "$.accessToken")

        val joinRequest = mockMvc.perform(
            post("/api/join-requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $mToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"inviteCode":"$inviteCode"}"""),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val joinRequestId: Int = JsonPath.read(joinRequest, "$.id")

        // E 가 승인 → M 에게 JOIN_REQUEST 알림 생성
        mockMvc.perform(
            patch("/api/join-requests/$joinRequestId")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $eToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"decision":"APPROVE"}"""),
        ).andExpect(status().isOk)

        // M 인박스: 응답 필드가 isRead 인지 (read 아님) 확인
        val inbox = mockMvc.perform(
            get("/api/notifications").header(HttpHeaders.AUTHORIZATION, "Bearer $mToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].isRead", `is`(false)))
            .andExpect(jsonPath("$.content[0].read").doesNotExist())
            .andReturn().response.contentAsString
        val notificationId: Int = JsonPath.read(inbox, "$.content[0].id")

        // 소유권: E 가 M 의 알림을 읽음 처리 시도 → 404
        mockMvc.perform(
            patch("/api/notifications/$notificationId/read")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $eToken"),
        ).andExpect(status().isNotFound)

        // 본인(M)은 읽음 처리 가능 → 204, 이후 안 읽은 수 0
        mockMvc.perform(
            patch("/api/notifications/$notificationId/read")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $mToken"),
        ).andExpect(status().isNoContent)
        mockMvc.perform(
            get("/api/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, "Bearer $mToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.count", `is`(0)))
    }

    @Test
    fun checkInWithValidQrTokenSucceeds() {
        val token = loginAs("employee@ptmanager.test")
        val qrToken = qrCodeService.issue(1) // 매장 1의 QR
        mockMvc.perform(
            post("/api/shifts/1/check-in")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"qrToken":"$qrToken"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.checkedInAt").exists())
    }

    @Test
    fun checkInWithForgedQrTokenIsRejected() {
        val token = loginAs("employee@ptmanager.test")
        mockMvc.perform(
            post("/api/shifts/2/check-in")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"qrToken":"wp1:1719740400:deadbeef"}"""),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun createSwapRequestReturnsPendingStatus() {
        val token = loginAs("employee@ptmanager.test")
        mockMvc.perform(
            post("/api/swap-requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "shiftId": 1,
                      "reason": "Personal schedule"
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.status", `is`("PENDING")))
    }
}

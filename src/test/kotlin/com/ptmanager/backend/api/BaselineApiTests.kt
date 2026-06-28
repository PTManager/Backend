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

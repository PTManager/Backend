package com.ptmanager.backend.swaprequest

import com.ptmanager.backend.domain.SwapApplication
import com.ptmanager.backend.domain.SwapRequestStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/swap-applications")
class SwapApplicationController(
    private val swapRequestService: SwapRequestService,
) {

    /** 내가 지원한 대타 내역 (직원 대타 탭의 '내가 지원한'). */
    @GetMapping("/me")
    fun myApplications(
        @AuthenticationPrincipal userId: Long,
        @RequestParam(required = false) status: SwapRequestStatus?,
    ): List<SwapApplication> = swapRequestService.myApplications(userId, status)
}

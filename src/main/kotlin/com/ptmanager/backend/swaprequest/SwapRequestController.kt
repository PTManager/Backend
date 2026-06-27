package com.ptmanager.backend.swaprequest

import com.ptmanager.backend.domain.SwapApplication
import com.ptmanager.backend.domain.SwapRequest
import com.ptmanager.backend.domain.SwapRequestStatus
import com.ptmanager.backend.swaprequest.dto.ApproveSwapRequest
import com.ptmanager.backend.swaprequest.dto.CreateSwapRequest
import com.ptmanager.backend.swaprequest.dto.SwapRequestDetail
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/swap-requests")
class SwapRequestController(
    private val swapRequestService: SwapRequestService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createSwapRequest(
        @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: CreateSwapRequest,
    ): SwapRequest = swapRequestService.createSwapRequest(userId, request.shiftId, request.reason)

    @GetMapping
    fun findSwapRequests(
        @AuthenticationPrincipal userId: Long,
        @RequestParam workplaceId: Long,
        @RequestParam view: String,
        @RequestParam(required = false) status: SwapRequestStatus?,
    ): List<SwapRequest> = swapRequestService.findSwapRequests(workplaceId, view, userId, status)

    @GetMapping("/{swapRequestId}")
    fun getDetail(@PathVariable swapRequestId: Long): SwapRequestDetail =
        swapRequestService.getDetail(swapRequestId)

    @PostMapping("/{swapRequestId}/applications")
    @ResponseStatus(HttpStatus.CREATED)
    fun apply(
        @AuthenticationPrincipal userId: Long,
        @PathVariable swapRequestId: Long,
    ): SwapApplication = swapRequestService.apply(swapRequestId, userId)

    @GetMapping("/{swapRequestId}/applications")
    fun listApplications(@PathVariable swapRequestId: Long): List<SwapApplication> =
        swapRequestService.listApplications(swapRequestId)

    @PostMapping("/{swapRequestId}/approve")
    fun approve(
        @PathVariable swapRequestId: Long,
        @Valid @RequestBody request: ApproveSwapRequest,
    ): SwapRequest = swapRequestService.approve(swapRequestId, request.applicantId)

    @PostMapping("/{swapRequestId}/reject")
    fun reject(@PathVariable swapRequestId: Long): SwapRequest = swapRequestService.reject(swapRequestId)
}

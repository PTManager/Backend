package com.ptmanager.backend.swap

import com.ptmanager.backend.domain.SwapRequest
import com.ptmanager.backend.domain.SwapRequestStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/swap-requests")
class SwapRequestController(
    private val swapRequestService: SwapRequestService,
) {

    @GetMapping
    fun findSwapRequests(): List<SwapRequest> = swapRequestService.findSwapRequests()

    @PostMapping
    fun createSwapRequest(@Valid @RequestBody request: CreateSwapRequest): SwapRequest =
        swapRequestService.createSwapRequest(
            request.requesterId,
            request.substituteId,
            request.workDate,
            request.reason,
        )

    @PatchMapping("/{id}/status")
    fun updateStatus(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateSwapRequestStatus,
    ): SwapRequest = swapRequestService.updateStatus(id, request.status)

    data class CreateSwapRequest(
        val requesterId: Long,
        val substituteId: Long?,
        @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE) val workDate: LocalDate,
        @field:NotBlank val reason: String,
    )

    data class UpdateSwapRequestStatus(
        val status: SwapRequestStatus,
    )
}

package com.ptmanager.backend.joinrequest

import com.ptmanager.backend.domain.JoinRequestStatus
import com.ptmanager.backend.joinrequest.dto.CreateJoinRequest
import com.ptmanager.backend.joinrequest.dto.DecisionRequest
import com.ptmanager.backend.joinrequest.dto.JoinRequestResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/join-requests")
class JoinRequestController(
    private val joinRequestService: JoinRequestService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createJoinRequest(
        @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: CreateJoinRequest,
    ): JoinRequestResponse = joinRequestService.create(request.inviteCode, userId)

    @GetMapping
    @PreAuthorize("hasRole('EMPLOYER')")
    fun findJoinRequests(
        @RequestParam workplaceId: Long,
        @RequestParam(required = false, defaultValue = "PENDING") status: JoinRequestStatus,
    ): List<JoinRequestResponse> = joinRequestService.findByWorkplace(workplaceId, status)

    @PatchMapping("/{joinRequestId}")
    @PreAuthorize("hasRole('EMPLOYER')")
    fun decide(
        @PathVariable joinRequestId: Long,
        @Valid @RequestBody request: DecisionRequest,
    ): JoinRequestResponse {
        val status = when (request.decision) {
            DecisionRequest.Decision.APPROVE -> JoinRequestStatus.APPROVED
            DecisionRequest.Decision.REJECT -> JoinRequestStatus.REJECTED
        }
        return joinRequestService.updateStatus(joinRequestId, status)
    }
}

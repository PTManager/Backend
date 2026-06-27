package com.ptmanager.backend.join

import com.ptmanager.backend.domain.JoinRequest
import com.ptmanager.backend.domain.JoinRequestStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
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

    @GetMapping
    fun findJoinRequests(@RequestParam workplaceId: Long): List<JoinRequest> =
        joinRequestService.findByWorkplace(workplaceId)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createJoinRequest(@Valid @RequestBody request: CreateJoinRequest): JoinRequest =
        joinRequestService.create(request.inviteCode, request.userId)

    @PatchMapping("/{id}/status")
    fun updateStatus(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateJoinRequestStatus,
    ): JoinRequest = joinRequestService.updateStatus(id, request.status)

    data class CreateJoinRequest(
        @field:NotBlank val inviteCode: String,
        val userId: Long,
    )

    data class UpdateJoinRequestStatus(
        val status: JoinRequestStatus,
    )
}

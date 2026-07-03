package com.ptmanager.backend.handover

import com.ptmanager.backend.domain.HandoverCategory
import com.ptmanager.backend.handover.dto.CreateHandoverRequest
import com.ptmanager.backend.handover.dto.HandoverResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 인수인계 노트 API. 공지(사장 전용)와 달리 매장 멤버(직원·사장) 누구나 작성/조회한다.
 */
@RestController
@RequestMapping("/api/handovers")
class HandoverController(
    private val handoverService: HandoverService,
) {

    @GetMapping
    fun findHandovers(
        @RequestParam workplaceId: Long,
        @RequestParam(required = false) category: HandoverCategory?,
    ): List<HandoverResponse> = handoverService.findByWorkplace(workplaceId, category)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createHandover(
        @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: CreateHandoverRequest,
    ): HandoverResponse = handoverService.create(
        request.workplaceId,
        userId,
        request.category,
        request.title,
        request.content,
    )

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteHandover(@PathVariable id: Long) = handoverService.delete(id)
}

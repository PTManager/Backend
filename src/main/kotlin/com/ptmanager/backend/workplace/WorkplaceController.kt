package com.ptmanager.backend.workplace

import com.ptmanager.backend.domain.User
import com.ptmanager.backend.domain.UserRole
import com.ptmanager.backend.domain.Workplace
import com.ptmanager.backend.workplace.dto.CreateWorkplaceRequest
import com.ptmanager.backend.workplace.dto.QrTokenResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/workplaces")
class WorkplaceController(
    private val workplaceService: WorkplaceService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('EMPLOYER')")
    fun createWorkplace(@Valid @RequestBody request: CreateWorkplaceRequest): Workplace =
        workplaceService.createWorkplace(request.name, request.address)

    @GetMapping("/{workplaceId}")
    fun getWorkplace(@PathVariable workplaceId: Long): Workplace =
        workplaceService.getWorkplace(workplaceId)

    @GetMapping("/{workplaceId}/members")
    fun members(
        @PathVariable workplaceId: Long,
        @RequestParam(required = false) role: UserRole?,
    ): List<User> = workplaceService.findMembers(workplaceId, role)

    @GetMapping("/{workplaceId}/qr-token")
    @PreAuthorize("hasRole('EMPLOYER')")
    fun qrToken(@PathVariable workplaceId: Long): QrTokenResponse =
        QrTokenResponse(workplaceService.issueQrToken(workplaceId))
}

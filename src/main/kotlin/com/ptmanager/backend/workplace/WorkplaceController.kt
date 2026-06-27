package com.ptmanager.backend.workplace

import com.ptmanager.backend.domain.Workplace
import com.ptmanager.backend.labor.LaborCostReport
import com.ptmanager.backend.labor.LaborCostService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/workplaces")
class WorkplaceController(
    private val workplaceService: WorkplaceService,
    private val laborCostService: LaborCostService,
) {

    @GetMapping
    fun findWorkplaces(): List<Workplace> = workplaceService.findWorkplaces()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createWorkplace(@Valid @RequestBody request: CreateWorkplaceRequest): Workplace =
        workplaceService.createWorkplace(request.name, request.address)

    @GetMapping("/{id}/labor-cost")
    fun laborCost(
        @PathVariable id: Long,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): LaborCostReport = laborCostService.calculate(id, from, to)

    data class CreateWorkplaceRequest(
        @field:NotBlank val name: String,
        val address: String?,
    )
}

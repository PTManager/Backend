package com.ptmanager.backend.health

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/health")
class HealthController {

    @GetMapping
    fun health(): HealthResponse = HealthResponse("UP", Instant.now())

    data class HealthResponse(val status: String, val timestamp: Instant)
}

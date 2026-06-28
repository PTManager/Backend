package com.ptmanager.backend.repository

import com.ptmanager.backend.domain.SwapRequest
import com.ptmanager.backend.domain.SwapRequestStatus
import org.springframework.data.jpa.repository.JpaRepository

interface SwapRequestRepository : JpaRepository<SwapRequest, Long> {

    fun findByWorkplaceIdOrderByCreatedAtDesc(workplaceId: Long): List<SwapRequest>

    fun findAllByOrderByCreatedAtDesc(): List<SwapRequest>

    fun existsByShiftIdAndStatus(shiftId: Long, status: SwapRequestStatus): Boolean
}

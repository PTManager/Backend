package com.ptmanager.backend.repository

import com.ptmanager.backend.domain.JoinRequest
import com.ptmanager.backend.domain.JoinRequestStatus
import org.springframework.data.jpa.repository.JpaRepository

interface JoinRequestRepository : JpaRepository<JoinRequest, Long> {

    fun findByWorkplaceIdOrderByCreatedAtDesc(workplaceId: Long): List<JoinRequest>

    fun findByWorkplaceIdAndStatusOrderByCreatedAtDesc(
        workplaceId: Long,
        status: JoinRequestStatus,
    ): List<JoinRequest>
}

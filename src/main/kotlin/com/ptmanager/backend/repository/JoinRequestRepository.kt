package com.ptmanager.backend.repository

import com.ptmanager.backend.domain.JoinRequest
import org.springframework.data.jpa.repository.JpaRepository

interface JoinRequestRepository : JpaRepository<JoinRequest, Long> {

    fun findByWorkplaceIdOrderByCreatedAtDesc(workplaceId: Long): List<JoinRequest>
}

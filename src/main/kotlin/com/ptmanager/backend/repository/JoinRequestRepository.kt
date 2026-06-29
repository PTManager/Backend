package com.ptmanager.backend.repository

import com.ptmanager.backend.domain.JoinRequest
import com.ptmanager.backend.domain.JoinRequestStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface JoinRequestRepository : JpaRepository<JoinRequest, Long> {

    fun findByWorkplaceIdAndStatusOrderByCreatedAtDesc(
        workplaceId: Long,
        status: JoinRequestStatus,
    ): List<JoinRequest>

    fun existsByWorkplaceIdAndUserIdAndStatus(
        workplaceId: Long,
        userId: Long,
        status: JoinRequestStatus,
    ): Boolean

    /** 상태 전이(승인/거절): expectedStatus(PENDING)일 때만 원자적으로 갱신. 영향 행 0이면 이미 처리됨. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update JoinRequest j set j.status = :newStatus where j.id = :id and j.status = :expectedStatus")
    fun markStatus(
        @Param("id") id: Long,
        @Param("newStatus") newStatus: JoinRequestStatus,
        @Param("expectedStatus") expectedStatus: JoinRequestStatus,
    ): Int
}

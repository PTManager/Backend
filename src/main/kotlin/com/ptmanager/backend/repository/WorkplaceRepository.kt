package com.ptmanager.backend.repository

import com.ptmanager.backend.domain.Workplace
import org.springframework.data.jpa.repository.JpaRepository

interface WorkplaceRepository : JpaRepository<Workplace, Long> {

    fun findByInviteCode(inviteCode: String): Workplace?

    fun existsByInviteCode(inviteCode: String): Boolean
}

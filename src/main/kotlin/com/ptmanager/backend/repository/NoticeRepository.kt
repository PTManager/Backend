package com.ptmanager.backend.repository

import com.ptmanager.backend.domain.Notice
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface NoticeRepository : JpaRepository<Notice, Long> {

    fun findByWorkplaceIdOrderByCreatedAtDesc(workplaceId: Long, pageable: Pageable): Page<Notice>

    fun findFirstByWorkplaceIdOrderByCreatedAtDesc(workplaceId: Long): Notice?
}

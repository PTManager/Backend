package com.ptmanager.backend.repository

import com.ptmanager.backend.domain.Notice
import org.springframework.data.jpa.repository.JpaRepository

interface NoticeRepository : JpaRepository<Notice, Long> {

    fun findByWorkplaceIdOrderByCreatedAtDesc(workplaceId: Long): List<Notice>
}

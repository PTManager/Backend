package com.ptmanager.backend.repository

import com.ptmanager.backend.domain.HandoverCategory
import com.ptmanager.backend.domain.HandoverNote
import org.springframework.data.jpa.repository.JpaRepository

interface HandoverNoteRepository : JpaRepository<HandoverNote, Long> {

    fun findByWorkplaceIdOrderByCreatedAtDesc(workplaceId: Long): List<HandoverNote>

    fun findByWorkplaceIdAndCategoryOrderByCreatedAtDesc(
        workplaceId: Long,
        category: HandoverCategory,
    ): List<HandoverNote>
}

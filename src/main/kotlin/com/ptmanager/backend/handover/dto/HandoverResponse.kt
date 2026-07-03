package com.ptmanager.backend.handover.dto

import com.ptmanager.backend.domain.HandoverCategory
import java.time.Instant

/** 인수인계 노트 응답. 작성자명을 포함한다. */
data class HandoverResponse(
    val id: Long?,
    val workplaceId: Long,
    val authorId: Long,
    val authorName: String?,
    val category: HandoverCategory,
    val title: String,
    val content: String,
    val createdAt: Instant?,
)

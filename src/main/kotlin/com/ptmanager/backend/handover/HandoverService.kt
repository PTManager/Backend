package com.ptmanager.backend.handover

import com.ptmanager.backend.common.access.WorkplaceAccessGuard
import com.ptmanager.backend.common.orNotFound
import com.ptmanager.backend.domain.HandoverCategory
import com.ptmanager.backend.domain.HandoverNote
import com.ptmanager.backend.handover.dto.HandoverResponse
import com.ptmanager.backend.repository.HandoverNoteRepository
import com.ptmanager.backend.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class HandoverService(
    private val handoverNoteRepository: HandoverNoteRepository,
    private val userRepository: UserRepository,
    private val accessGuard: WorkplaceAccessGuard,
) {

    /** 매장의 인수인계 노트를 최신순으로. category 가 있으면 해당 분류만. */
    fun findByWorkplace(workplaceId: Long, category: HandoverCategory?): List<HandoverResponse> {
        accessGuard.requireMemberOf(workplaceId)
        val notes = if (category == null) {
            handoverNoteRepository.findByWorkplaceIdOrderByCreatedAtDesc(workplaceId)
        } else {
            handoverNoteRepository.findByWorkplaceIdAndCategoryOrderByCreatedAtDesc(workplaceId, category)
        }
        val authorNames = userRepository.findAllById(notes.map { it.authorId }.distinct())
            .associate { it.id to it.name }
        return notes.map { toResponse(it, authorNames[it.authorId]) }
    }

    @Transactional
    fun create(
        workplaceId: Long,
        authorId: Long,
        category: HandoverCategory,
        title: String,
        content: String,
    ): HandoverResponse {
        accessGuard.requireMemberOf(workplaceId)
        val note = handoverNoteRepository.save(
            HandoverNote(
                workplaceId = workplaceId,
                authorId = authorId,
                category = category,
                title = title,
                content = content,
            ),
        )
        return toResponse(note, authorNameOf(authorId))
    }

    @Transactional
    fun delete(id: Long) {
        val note = handoverNoteRepository.findById(id).orNotFound("Handover note not found.")
        accessGuard.requireMemberOf(note.workplaceId)
        // 본인이 쓴 노트만 삭제할 수 있다.
        if (note.authorId != accessGuard.currentUserId()) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "본인이 작성한 노트만 삭제할 수 있습니다.")
        }
        handoverNoteRepository.delete(note)
    }

    private fun authorNameOf(authorId: Long): String? =
        userRepository.findById(authorId).orElse(null)?.name

    private fun toResponse(note: HandoverNote, authorName: String?): HandoverResponse =
        HandoverResponse(
            id = note.id,
            workplaceId = note.workplaceId,
            authorId = note.authorId,
            authorName = authorName,
            category = note.category,
            title = note.title,
            content = note.content,
            createdAt = note.createdAt,
        )
}

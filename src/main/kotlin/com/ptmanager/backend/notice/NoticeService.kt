package com.ptmanager.backend.notice

import com.ptmanager.backend.domain.Notice
import com.ptmanager.backend.repository.NoticeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.NoSuchElementException

@Service
class NoticeService(
    private val noticeRepository: NoticeRepository,
) {

    fun findByWorkplace(workplaceId: Long): List<Notice> =
        noticeRepository.findByWorkplaceIdOrderByCreatedAtDesc(workplaceId)

    fun findById(id: Long): Notice =
        noticeRepository.findById(id)
            .orElseThrow { NoSuchElementException("Notice not found.") }

    @Transactional
    fun create(workplaceId: Long, authorId: Long, title: String, body: String): Notice {
        val notice = Notice(
            workplaceId = workplaceId,
            authorId = authorId,
            title = title,
            body = body,
            createdAt = Instant.now(),
        )
        return noticeRepository.save(notice)
    }
}

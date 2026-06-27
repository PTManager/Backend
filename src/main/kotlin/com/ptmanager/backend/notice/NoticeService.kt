package com.ptmanager.backend.notice

import com.ptmanager.backend.domain.Notice
import com.ptmanager.backend.domain.NoticeAttachment
import com.ptmanager.backend.repository.NoticeAttachmentRepository
import com.ptmanager.backend.repository.NoticeRepository
import com.ptmanager.backend.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.NoSuchElementException

@Service
class NoticeService(
    private val noticeRepository: NoticeRepository,
    private val noticeAttachmentRepository: NoticeAttachmentRepository,
    private val userRepository: UserRepository,
) {

    fun findByWorkplace(workplaceId: Long): List<Notice> =
        noticeRepository.findByWorkplaceIdOrderByCreatedAtDesc(workplaceId)

    fun findById(id: Long): Notice =
        noticeRepository.findById(id)
            .orElseThrow { NoSuchElementException("Notice not found.") }

    @Transactional
    fun create(
        workplaceId: Long,
        authorId: Long,
        title: String,
        body: String,
        attachmentUrls: List<String>,
    ): Notice {
        val notice = noticeRepository.save(
            Notice(workplaceId = workplaceId, authorId = authorId, title = title, body = body),
        )
        attachmentUrls.forEach { url ->
            noticeAttachmentRepository.save(NoticeAttachment(noticeId = notice.id!!, fileUrl = url))
        }
        return notice
    }

    @Transactional
    fun delete(id: Long) {
        val notice = findById(id)
        noticeRepository.delete(notice) // 첨부는 DB의 ON DELETE CASCADE 대상 (운영 PostgreSQL)
    }

    fun hasUnread(workplaceId: Long, userId: Long): Boolean {
        val latest = noticeRepository.findFirstByWorkplaceIdOrderByCreatedAtDesc(workplaceId)
            ?: return false
        val user = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("User not found.") }
        val lastRead = user.lastReadNoticeAt ?: return true
        val latestCreatedAt = latest.createdAt ?: return false
        return latestCreatedAt.isAfter(lastRead)
    }

    @Transactional
    fun markRead(userId: Long) {
        val user = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("User not found.") }
        user.lastReadNoticeAt = Instant.now()
        userRepository.save(user)
    }
}

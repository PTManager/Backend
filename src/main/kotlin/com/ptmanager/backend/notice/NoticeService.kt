package com.ptmanager.backend.notice

import com.ptmanager.backend.common.access.WorkplaceAccessGuard
import com.ptmanager.backend.common.storage.StorageService
import com.ptmanager.backend.domain.Notice
import com.ptmanager.backend.domain.NoticeAttachment
import com.ptmanager.backend.domain.NotificationType
import com.ptmanager.backend.repository.NoticeAttachmentRepository
import com.ptmanager.backend.repository.NoticeRepository
import com.ptmanager.backend.repository.UserRepository
import com.ptmanager.backend.notification.NotificationService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import java.util.NoSuchElementException

@Service
class NoticeService(
    private val noticeRepository: NoticeRepository,
    private val noticeAttachmentRepository: NoticeAttachmentRepository,
    private val userRepository: UserRepository,
    private val notificationService: NotificationService,
    private val storageService: StorageService,
    private val accessGuard: WorkplaceAccessGuard,
) {

    fun findByWorkplace(workplaceId: Long, pageable: Pageable): Page<Notice> {
        accessGuard.requireMemberOf(workplaceId)
        return noticeRepository.findByWorkplaceIdOrderByCreatedAtDesc(workplaceId, pageable)
    }

    fun findById(id: Long): Notice {
        val notice = noticeRepository.findById(id)
            .orElseThrow { NoSuchElementException("Notice not found.") }
        accessGuard.requireMemberOf(notice.workplaceId)
        return notice
    }

    @Transactional
    fun create(
        workplaceId: Long,
        authorId: Long,
        title: String,
        body: String,
        attachmentUrls: List<String>,
    ): Notice {
        accessGuard.requireMemberOf(workplaceId)
        val notice = noticeRepository.save(
            Notice(workplaceId = workplaceId, authorId = authorId, title = title, body = body),
        )
        attachmentUrls.forEach { url ->
            noticeAttachmentRepository.save(NoticeAttachment(noticeId = notice.id!!, fileUrl = url))
        }

        // 같은 매장 직원들에게 새 공지 알림 (작성자 제외)
        val recipients = userRepository.findByWorkplaceId(workplaceId)
            .mapNotNull { it.id }
            .filter { it != authorId }
        notificationService.notifyAll(
            recipients,
            NotificationType.NOTICE,
            "새 공지가 등록되었습니다: $title",
            targetType = "NOTICE",
            targetId = notice.id,
        )
        return notice
    }

    @Transactional
    fun delete(id: Long) {
        val notice = findById(id)
        noticeRepository.delete(notice) // 첨부는 DB의 ON DELETE CASCADE 대상 (운영 PostgreSQL)
    }

    /** 첨부 파일을 스토리지에 올리고 접근 URL을 담은 (비영속) NoticeAttachment 를 반환한다. */
    fun uploadAttachment(file: MultipartFile): NoticeAttachment =
        NoticeAttachment(fileUrl = storageService.store(file), createdAt = Instant.now())

    fun hasUnread(workplaceId: Long, userId: Long): Boolean {
        accessGuard.requireMemberOf(workplaceId)
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

package com.ptmanager.backend.notice

import com.ptmanager.backend.common.orNotFound

import com.ptmanager.backend.common.access.WorkplaceAccessGuard
import com.ptmanager.backend.common.storage.StorageService
import com.ptmanager.backend.domain.Notice
import com.ptmanager.backend.domain.NoticeAttachment
import com.ptmanager.backend.domain.NotificationType
import com.ptmanager.backend.notice.dto.NoticeResponse
import com.ptmanager.backend.notification.NotificationService
import com.ptmanager.backend.repository.NoticeAttachmentRepository
import com.ptmanager.backend.repository.NoticeRepository
import com.ptmanager.backend.repository.UserRepository
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

    fun findByWorkplace(workplaceId: Long, pageable: Pageable): Page<NoticeResponse> {
        accessGuard.requireMemberOf(workplaceId)
        val page = noticeRepository.findByWorkplaceIdOrderByCreatedAtDesc(workplaceId, pageable)
        val notices = page.content

        val authorNames = userRepository.findAllById(notices.map { it.authorId }.distinct())
            .associate { it.id to it.name }
        val attachmentsByNotice = noticeAttachmentRepository
            .findByNoticeIdIn(notices.mapNotNull { it.id })
            .groupBy { it.noticeId }

        return page.map {
            toResponse(it, authorNames[it.authorId], attachmentsByNotice[it.id] ?: emptyList())
        }
    }

    fun findById(id: Long): NoticeResponse {
        val notice = getNotice(id)
        return toResponse(notice, authorNameOf(notice.authorId), attachmentsOf(notice.id))
    }

    @Transactional
    fun create(
        workplaceId: Long,
        authorId: Long,
        title: String,
        body: String,
        attachmentUrls: List<String>,
    ): NoticeResponse {
        accessGuard.requireMemberOf(workplaceId)
        val notice = noticeRepository.save(
            Notice(workplaceId = workplaceId, authorId = authorId, title = title, body = body),
        )
        val attachments = attachmentUrls.map { url ->
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
        return toResponse(notice, authorNameOf(authorId), attachments)
    }

    @Transactional
    fun delete(id: Long) {
        val notice = getNotice(id)
        // 첨부를 먼저 제거한다. (plain FK라 DB 캐스케이드에 의존하지 않고 앱에서 정리)
        noticeAttachmentRepository.deleteByNoticeId(notice.id!!)
        noticeRepository.delete(notice)
    }

    /** 첨부 파일을 스토리지에 올리고 접근 URL을 담은 (비영속) NoticeAttachment 를 반환한다. */
    fun uploadAttachment(file: MultipartFile): NoticeAttachment =
        NoticeAttachment(fileUrl = storageService.store(file), createdAt = Instant.now())

    fun hasUnread(workplaceId: Long, userId: Long): Boolean {
        accessGuard.requireMemberOf(workplaceId)
        val latest = noticeRepository.findFirstByWorkplaceIdOrderByCreatedAtDesc(workplaceId)
            ?: return false
        val user = userRepository.findById(userId)
            .orNotFound("User not found.")
        val lastRead = user.lastReadNoticeAt ?: return true
        val latestCreatedAt = latest.createdAt ?: return false
        return latestCreatedAt.isAfter(lastRead)
    }

    @Transactional
    fun markRead(userId: Long) {
        val user = userRepository.findById(userId)
            .orNotFound("User not found.")
        user.lastReadNoticeAt = Instant.now()
        userRepository.save(user)
    }

    private fun getNotice(id: Long): Notice {
        val notice = noticeRepository.findById(id)
            .orNotFound("Notice not found.")
        accessGuard.requireMemberOf(notice.workplaceId)
        return notice
    }

    private fun authorNameOf(authorId: Long): String? =
        userRepository.findById(authorId).orElse(null)?.name

    private fun attachmentsOf(noticeId: Long?): List<NoticeAttachment> =
        if (noticeId == null) emptyList() else noticeAttachmentRepository.findByNoticeId(noticeId)

    private fun toResponse(
        notice: Notice,
        authorName: String?,
        attachments: List<NoticeAttachment>,
    ): NoticeResponse =
        NoticeResponse(
            id = notice.id,
            workplaceId = notice.workplaceId,
            authorId = notice.authorId,
            authorName = authorName,
            title = notice.title,
            body = notice.body,
            attachments = attachments,
            createdAt = notice.createdAt,
        )
}

package com.ptmanager.backend.notice

import com.ptmanager.backend.common.dto.PageResponse
import com.ptmanager.backend.domain.Notice
import com.ptmanager.backend.domain.NoticeAttachment
import com.ptmanager.backend.notice.dto.CreateNoticeRequest
import com.ptmanager.backend.notice.dto.UnreadFlag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.Instant

@RestController
@RequestMapping("/api/notices")
class NoticeController(
    private val noticeService: NoticeService,
) {

    @GetMapping
    fun findNotices(
        @RequestParam workplaceId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<Notice> {
        val all = noticeService.findByWorkplace(workplaceId)
        val fromIndex = (page * size).coerceIn(0, all.size)
        val toIndex = (fromIndex + size).coerceIn(0, all.size)
        val content = all.subList(fromIndex, toIndex)
        val totalPages = if (size == 0) 0 else ((all.size + size - 1) / size)
        return PageResponse(content, page, size, all.size.toLong(), totalPages)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createNotice(
        @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: CreateNoticeRequest,
    ): Notice = noticeService.create(
        request.workplaceId,
        userId,
        request.title,
        request.body,
        request.attachmentUrls,
    )

    @GetMapping("/{noticeId}")
    fun findNotice(@PathVariable noticeId: Long): Notice = noticeService.findById(noticeId)

    @DeleteMapping("/{noticeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteNotice(@PathVariable noticeId: Long) = noticeService.delete(noticeId)

    @PostMapping("/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    fun uploadAttachment(@RequestParam("file") file: MultipartFile): NoticeAttachment {
        // TODO: S3 업로드 연동. 현재는 목 응답.
        return NoticeAttachment(
            id = 0,
            noticeId = 0,
            fileUrl = "https://example.com/uploads/${file.originalFilename ?: "file"}",
            createdAt = Instant.now(),
        )
    }

    @GetMapping("/unread")
    fun unread(
        @AuthenticationPrincipal userId: Long,
        @RequestParam workplaceId: Long,
    ): UnreadFlag = UnreadFlag(noticeService.hasUnread(workplaceId, userId))

    @PostMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun read(@AuthenticationPrincipal userId: Long) = noticeService.markRead(userId)
}

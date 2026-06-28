package com.ptmanager.backend.notice

import com.ptmanager.backend.common.dto.PageResponse
import com.ptmanager.backend.domain.Notice
import com.ptmanager.backend.domain.NoticeAttachment
import com.ptmanager.backend.notice.dto.CreateNoticeRequest
import com.ptmanager.backend.notice.dto.UnreadFlag
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
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
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100))
        val result = noticeService.findByWorkplace(workplaceId, pageable)
        return PageResponse(
            result.content,
            result.number,
            result.size,
            result.totalElements,
            result.totalPages,
        )
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('EMPLOYER')")
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
    @PreAuthorize("hasRole('EMPLOYER')")
    fun deleteNotice(@PathVariable noticeId: Long) = noticeService.delete(noticeId)

    @PostMapping("/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('EMPLOYER')")
    fun uploadAttachment(@RequestParam("file") file: MultipartFile): NoticeAttachment =
        noticeService.uploadAttachment(file)

    @GetMapping("/unread")
    fun unread(
        @AuthenticationPrincipal userId: Long,
        @RequestParam workplaceId: Long,
    ): UnreadFlag = UnreadFlag(noticeService.hasUnread(workplaceId, userId))

    @PostMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun read(@AuthenticationPrincipal userId: Long) = noticeService.markRead(userId)
}

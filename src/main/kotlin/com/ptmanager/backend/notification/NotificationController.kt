package com.ptmanager.backend.notification

import com.ptmanager.backend.common.dto.PageResponse
import com.ptmanager.backend.domain.Notification
import com.ptmanager.backend.notification.dto.UnreadCount
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/notifications")
class NotificationController(
    private val notificationService: NotificationService,
) {

    @GetMapping
    fun findNotifications(
        @AuthenticationPrincipal userId: Long,
        @RequestParam(required = false) isRead: Boolean?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<Notification> {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100))
        val result = notificationService.findByUser(userId, isRead, pageable)
        return PageResponse(
            result.content,
            result.number,
            result.size,
            result.totalElements,
            result.totalPages,
        )
    }

    @GetMapping("/unread-count")
    fun unreadCount(@AuthenticationPrincipal userId: Long): UnreadCount =
        UnreadCount(notificationService.unreadCount(userId))

    @PatchMapping("/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markRead(
        @AuthenticationPrincipal userId: Long,
        @PathVariable notificationId: Long,
    ) = notificationService.markRead(notificationId, userId)

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markAllRead(@AuthenticationPrincipal userId: Long) = notificationService.markAllRead(userId)
}

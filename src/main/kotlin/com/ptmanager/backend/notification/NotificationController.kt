package com.ptmanager.backend.notification

import com.ptmanager.backend.domain.Notification
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/notifications")
class NotificationController(
    private val notificationService: NotificationService,
) {

    @GetMapping
    fun findNotifications(@RequestParam userId: Long): List<Notification> =
        notificationService.findByUser(userId)

    @PatchMapping("/{id}/read")
    fun markRead(@PathVariable id: Long): Notification = notificationService.markRead(id)
}

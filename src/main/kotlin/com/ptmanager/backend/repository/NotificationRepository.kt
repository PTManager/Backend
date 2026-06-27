package com.ptmanager.backend.repository

import com.ptmanager.backend.domain.Notification
import org.springframework.data.jpa.repository.JpaRepository

interface NotificationRepository : JpaRepository<Notification, Long> {

    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<Notification>
}

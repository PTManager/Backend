package com.ptmanager.backend.repository

import com.ptmanager.backend.domain.Notification
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface NotificationRepository : JpaRepository<Notification, Long> {

    fun findByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): Page<Notification>

    fun findByUserIdAndReadOrderByCreatedAtDesc(userId: Long, read: Boolean, pageable: Pageable): Page<Notification>

    /** markAllRead 용 (페이지네이션 없이 미읽음 전체). */
    fun findByUserIdAndRead(userId: Long, read: Boolean): List<Notification>

    fun countByUserIdAndReadFalse(userId: Long): Long
}

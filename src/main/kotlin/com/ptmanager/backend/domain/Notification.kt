package com.ptmanager.backend.domain

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(name = "notification")
class Notification(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    var type: NotificationType = NotificationType.NOTICE,

    @Column(nullable = false)
    var message: String = "",

    @Column(name = "target_type", length = 24)
    var targetType: String? = null,

    @Column(name = "target_id")
    var targetId: Long? = null,

    @get:JsonProperty("isRead")
    @Column(name = "is_read", nullable = false)
    var read: Boolean = false,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,
)

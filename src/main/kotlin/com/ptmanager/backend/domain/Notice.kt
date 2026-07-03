package com.ptmanager.backend.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(name = "notice")
class Notice(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "workplace_id", nullable = false)
    var workplaceId: Long = 0,

    @Column(name = "author_id", nullable = false)
    var authorId: Long = 0,

    @Column(nullable = false)
    var title: String = "",

    // PostgreSQL의 TEXT 컬럼. @Lob 을 붙이면 Hibernate가 Large Object(oid)로 취급해
    // 조회 시 "Bad value for type long" 예외가 나므로, 일반 문자열 매핑을 쓴다.
    @Column(nullable = false, columnDefinition = "text")
    var body: String = "",

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,
)

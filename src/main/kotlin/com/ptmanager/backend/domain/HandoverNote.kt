package com.ptmanager.backend.domain

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

/**
 * 인수인계 노트. 공지(Notice, 사장 전용)와 달리 같은 매장의 직원·사장이 모두 남길 수 있는
 * 교대 인수인계용 메모. 카테고리로 분류한다.
 */
@Entity
@Table(name = "handover_note")
class HandoverNote(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "workplace_id", nullable = false)
    var workplaceId: Long = 0,

    @Column(name = "author_id", nullable = false)
    var authorId: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var category: HandoverCategory = HandoverCategory.STOCK,

    @Column(nullable = false)
    var title: String = "",

    // PostgreSQL의 TEXT 컬럼. @Lob 을 붙이면 Hibernate가 Large Object(oid)로 취급해
    // 조회 시 예외가 나므로(Notice.body 와 동일), 일반 문자열 매핑을 쓴다.
    @Column(nullable = false, columnDefinition = "text")
    var content: String = "",

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,
)

package com.ptmanager.backend.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "swap_request")
class SwapRequest(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "workplace_id", nullable = false)
    var workplaceId: Long = 0,

    @Column(name = "requester_id", nullable = false)
    var requesterId: Long = 0,

    @Column(name = "substitute_id")
    var substituteId: Long? = null,

    @Column(name = "work_date", nullable = false)
    var workDate: LocalDate = LocalDate.MIN,

    @Column(nullable = false)
    var reason: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: SwapRequestStatus = SwapRequestStatus.PENDING,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,
)

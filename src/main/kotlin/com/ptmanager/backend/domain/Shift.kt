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
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Entity
@Table(name = "shift")
class Shift(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "workplace_id", nullable = false)
    var workplaceId: Long = 0,

    @Column(name = "employee_id", nullable = false)
    var employeeId: Long = 0,

    @Column(name = "work_date", nullable = false)
    var workDate: LocalDate = LocalDate.MIN,

    @Column(name = "start_time", nullable = false)
    var startTime: LocalTime = LocalTime.MIN,

    @Column(name = "end_time", nullable = false)
    var endTime: LocalTime = LocalTime.MIN,

    @Column(name = "checked_in_at")
    var checkedInAt: Instant? = null,

    @Column(name = "checked_out_at")
    var checkedOutAt: Instant? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_status", nullable = false, length = 16)
    var attendanceStatus: AttendanceStatus = AttendanceStatus.SCHEDULED,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
)

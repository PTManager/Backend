package com.ptmanager.backend.repository

import com.ptmanager.backend.domain.User
import com.ptmanager.backend.domain.UserRole
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {

    fun findByEmailIgnoreCase(email: String): User?

    fun findByWorkplaceId(workplaceId: Long): List<User>

    fun findByWorkplaceIdAndRole(workplaceId: Long, role: UserRole): List<User>
}

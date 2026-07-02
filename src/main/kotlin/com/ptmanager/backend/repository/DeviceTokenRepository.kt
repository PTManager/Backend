package com.ptmanager.backend.repository

import com.ptmanager.backend.domain.DeviceToken
import org.springframework.data.jpa.repository.JpaRepository

interface DeviceTokenRepository : JpaRepository<DeviceToken, Long> {

    fun findByToken(token: String): DeviceToken?

    fun findByUserId(userId: Long): List<DeviceToken>

    fun findByUserIdIn(userIds: Collection<Long>): List<DeviceToken>

    fun deleteByTokenIn(tokens: Collection<String>)
}

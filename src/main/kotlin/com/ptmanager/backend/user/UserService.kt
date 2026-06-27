package com.ptmanager.backend.user

import com.ptmanager.backend.domain.DeviceToken
import com.ptmanager.backend.domain.NotificationSetting
import com.ptmanager.backend.domain.Platform
import com.ptmanager.backend.domain.User
import com.ptmanager.backend.repository.DeviceTokenRepository
import com.ptmanager.backend.repository.NotificationSettingRepository
import com.ptmanager.backend.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.NoSuchElementException

@Service
class UserService(
    private val userRepository: UserRepository,
    private val notificationSettingRepository: NotificationSettingRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
) {

    @Transactional
    fun updateProfile(userId: Long, name: String): User {
        val user = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("User not found.") }
        user.name = name
        return userRepository.save(user)
    }

    fun getNotificationSetting(userId: Long): NotificationSetting =
        notificationSettingRepository.findById(userId)
            .orElseGet { NotificationSetting(userId = userId) }

    @Transactional
    fun updateNotificationSetting(
        userId: Long,
        swapEnabled: Boolean?,
        noticeEnabled: Boolean?,
        attendanceEnabled: Boolean?,
        joinRequestEnabled: Boolean?,
    ): NotificationSetting {
        val setting = notificationSettingRepository.findById(userId)
            .orElseGet { NotificationSetting(userId = userId) }
        swapEnabled?.let { setting.swapEnabled = it }
        noticeEnabled?.let { setting.noticeEnabled = it }
        attendanceEnabled?.let { setting.attendanceEnabled = it }
        joinRequestEnabled?.let { setting.joinRequestEnabled = it }
        return notificationSettingRepository.save(setting)
    }

    @Transactional
    fun registerDeviceToken(userId: Long, token: String, platform: Platform): DeviceToken {
        val deviceToken = deviceTokenRepository.findByToken(token)
            ?: DeviceToken(token = token)
        deviceToken.userId = userId
        deviceToken.platform = platform
        return deviceTokenRepository.save(deviceToken)
    }

    @Transactional
    fun deleteDeviceToken(userId: Long, token: String) {
        val deviceToken = deviceTokenRepository.findByToken(token)
            ?: throw NoSuchElementException("Device token not found.")
        if (deviceToken.userId == userId) {
            deviceTokenRepository.delete(deviceToken)
        }
    }
}

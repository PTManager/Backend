package com.ptmanager.backend.user

import com.ptmanager.backend.domain.DeviceToken
import com.ptmanager.backend.domain.NotificationSetting
import com.ptmanager.backend.domain.User
import com.ptmanager.backend.user.dto.NotificationSettingUpdate
import com.ptmanager.backend.user.dto.RegisterDeviceTokenRequest
import com.ptmanager.backend.user.dto.UpdateProfileRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService,
) {

    @PatchMapping("/me")
    fun updateProfile(
        @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: UpdateProfileRequest,
    ): User = userService.updateProfile(userId, request.name)

    @GetMapping("/me/notification-setting")
    fun getNotificationSetting(@AuthenticationPrincipal userId: Long): NotificationSetting =
        userService.getNotificationSetting(userId)

    @PatchMapping("/me/notification-setting")
    fun updateNotificationSetting(
        @AuthenticationPrincipal userId: Long,
        @RequestBody request: NotificationSettingUpdate,
    ): NotificationSetting = userService.updateNotificationSetting(
        userId,
        request.swapEnabled,
        request.noticeEnabled,
        request.attendanceEnabled,
        request.joinRequestEnabled,
    )

    @PostMapping("/me/device-tokens")
    @ResponseStatus(HttpStatus.CREATED)
    fun registerDeviceToken(
        @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: RegisterDeviceTokenRequest,
    ): DeviceToken = userService.registerDeviceToken(userId, request.token, request.platform)

    @DeleteMapping("/me/device-tokens/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteDeviceToken(
        @AuthenticationPrincipal userId: Long,
        @PathVariable token: String,
    ) = userService.deleteDeviceToken(userId, token)
}

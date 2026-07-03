package com.ptmanager.backend.workplace

import com.ptmanager.backend.common.orNotFound

import com.ptmanager.backend.common.access.WorkplaceAccessGuard
import com.ptmanager.backend.domain.User
import com.ptmanager.backend.domain.UserRole
import com.ptmanager.backend.domain.Workplace
import com.ptmanager.backend.repository.UserRepository
import com.ptmanager.backend.repository.WorkplaceRepository
import com.ptmanager.backend.shift.QrCodeService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.security.SecureRandom
import java.util.NoSuchElementException

@Service
class WorkplaceService(
    private val workplaceRepository: WorkplaceRepository,
    private val userRepository: UserRepository,
    private val accessGuard: WorkplaceAccessGuard,
    private val qrCodeService: QrCodeService,
) {

    /** 매장 QR 출근 토큰을 발급한다. (사장이 매장에 게시) */
    fun issueQrToken(workplaceId: Long): String {
        accessGuard.requireMemberOf(workplaceId)
        return qrCodeService.issue(workplaceId)
    }

    fun getWorkplace(id: Long): Workplace {
        accessGuard.requireMemberOf(id)
        return workplaceRepository.findById(id)
            .orNotFound("Workplace not found.")
    }

    fun findMembers(workplaceId: Long, role: UserRole?): List<User> {
        accessGuard.requireMemberOf(workplaceId)
        return if (role == null) {
            userRepository.findByWorkplaceId(workplaceId)
        } else {
            userRepository.findByWorkplaceIdAndRole(workplaceId, role)
        }
    }

    /** 매장 멤버의 시급을 설정한다. (사장) */
    @Transactional
    fun updateMemberWage(workplaceId: Long, userId: Long, hourlyWage: Int): User {
        accessGuard.requireMemberOf(workplaceId)
        val user = userRepository.findById(userId)
            .orNotFound("User not found.")
        // 해당 매장 소속 멤버가 아니면 존재를 숨긴다.
        if (user.workplaceId != workplaceId) {
            throw NoSuchElementException("User not found.")
        }
        user.hourlyWage = hourlyWage
        return userRepository.save(user)
    }

    /** 매장에서 멤버를 내보낸다. (사장) 유저 계정은 유지하고 매장 소속만 해제한다. */
    @Transactional
    fun removeMember(workplaceId: Long, userId: Long) {
        accessGuard.requireMemberOf(workplaceId)
        // 사장이 자기 자신을 내보내면 매장이 주인 없이 남으므로 막는다.
        if (userId == accessGuard.currentUserId()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "자기 자신은 내보낼 수 없습니다.")
        }
        val user = userRepository.findById(userId)
            .orNotFound("User not found.")
        // 해당 매장 소속 멤버가 아니면 존재를 숨긴다.
        if (user.workplaceId != workplaceId) {
            throw NoSuchElementException("User not found.")
        }
        user.workplaceId = null
        userRepository.save(user)
    }

    @Transactional
    fun createWorkplace(name: String, address: String?): Workplace {
        val workplace = workplaceRepository.save(
            Workplace(name = name, address = address, inviteCode = generateUniqueInviteCode()),
        )
        // 생성자는 해당 매장에 소속된다.
        val creator = userRepository.findById(accessGuard.currentUserId())
            .orNotFound("User not found.")
        creator.workplaceId = workplace.id
        userRepository.save(creator)
        return workplace
    }

    private fun generateUniqueInviteCode(): String {
        var code: String
        do {
            code = buildString {
                repeat(CODE_LENGTH) {
                    append(CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)])
                }
            }
        } while (workplaceRepository.existsByInviteCode(code))
        return code
    }

    companion object {
        private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val CODE_LENGTH = 6
        private val RANDOM = SecureRandom()
    }
}

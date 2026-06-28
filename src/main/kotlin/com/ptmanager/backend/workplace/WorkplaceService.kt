package com.ptmanager.backend.workplace

import com.ptmanager.backend.common.access.WorkplaceAccessGuard
import com.ptmanager.backend.domain.User
import com.ptmanager.backend.domain.UserRole
import com.ptmanager.backend.domain.Workplace
import com.ptmanager.backend.repository.UserRepository
import com.ptmanager.backend.repository.WorkplaceRepository
import com.ptmanager.backend.shift.QrCodeService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
            .orElseThrow { NoSuchElementException("Workplace not found.") }
    }

    fun findMembers(workplaceId: Long, role: UserRole?): List<User> {
        accessGuard.requireMemberOf(workplaceId)
        return if (role == null) {
            userRepository.findByWorkplaceId(workplaceId)
        } else {
            userRepository.findByWorkplaceIdAndRole(workplaceId, role)
        }
    }

    @Transactional
    fun createWorkplace(name: String, address: String?): Workplace {
        val workplace = workplaceRepository.save(
            Workplace(name = name, address = address, inviteCode = generateUniqueInviteCode()),
        )
        // 생성자는 해당 매장에 소속된다.
        val creator = userRepository.findById(accessGuard.currentUserId())
            .orElseThrow { NoSuchElementException("User not found.") }
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

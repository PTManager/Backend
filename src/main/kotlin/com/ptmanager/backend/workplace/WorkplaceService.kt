package com.ptmanager.backend.workplace

import com.ptmanager.backend.domain.User
import com.ptmanager.backend.domain.UserRole
import com.ptmanager.backend.domain.Workplace
import com.ptmanager.backend.repository.UserRepository
import com.ptmanager.backend.repository.WorkplaceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.util.NoSuchElementException

@Service
class WorkplaceService(
    private val workplaceRepository: WorkplaceRepository,
    private val userRepository: UserRepository,
) {

    fun getWorkplace(id: Long): Workplace =
        workplaceRepository.findById(id)
            .orElseThrow { NoSuchElementException("Workplace not found.") }

    fun findMembers(workplaceId: Long, role: UserRole?): List<User> =
        if (role == null) {
            userRepository.findByWorkplaceId(workplaceId)
        } else {
            userRepository.findByWorkplaceIdAndRole(workplaceId, role)
        }

    @Transactional
    fun createWorkplace(name: String, address: String?): Workplace {
        val workplace = Workplace(name = name, address = address, inviteCode = generateUniqueInviteCode())
        return workplaceRepository.save(workplace)
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

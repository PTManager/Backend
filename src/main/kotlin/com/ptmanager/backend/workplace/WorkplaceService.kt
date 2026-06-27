package com.ptmanager.backend.workplace

import com.ptmanager.backend.domain.Workplace
import com.ptmanager.backend.repository.WorkplaceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom

@Service
class WorkplaceService(
    private val workplaceRepository: WorkplaceRepository,
) {

    fun findWorkplaces(): List<Workplace> = workplaceRepository.findAll()

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

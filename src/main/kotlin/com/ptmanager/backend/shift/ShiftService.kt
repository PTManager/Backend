package com.ptmanager.backend.shift

import com.ptmanager.backend.domain.Shift
import com.ptmanager.backend.repository.ShiftRepository
import com.ptmanager.backend.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.NoSuchElementException

@Service
class ShiftService(
    private val shiftRepository: ShiftRepository,
    private val userRepository: UserRepository,
) {

    fun findShiftsByUser(userId: Long): List<Shift> {
        if (!userRepository.existsById(userId)) {
            throw NoSuchElementException("User not found.")
        }
        return shiftRepository.findByEmployeeIdOrderByWorkDateAscStartTimeAsc(userId)
    }

    @Transactional
    fun checkIn(shiftId: Long): Shift {
        val shift = shiftRepository.findById(shiftId)
            .orElseThrow { NoSuchElementException("Shift not found.") }
        require(!shift.checkedIn) { "Shift is already checked in." }
        shift.checkedIn = true
        return shiftRepository.save(shift)
    }
}

package com.ptmanager.backend.config

import com.ptmanager.backend.domain.Shift
import com.ptmanager.backend.domain.User
import com.ptmanager.backend.domain.UserRole
import com.ptmanager.backend.domain.Workplace
import com.ptmanager.backend.repository.ShiftRepository
import com.ptmanager.backend.repository.UserRepository
import com.ptmanager.backend.repository.WorkplaceRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalTime

@Component
class DataSeeder(
    private val workplaceRepository: WorkplaceRepository,
    private val userRepository: UserRepository,
    private val shiftRepository: ShiftRepository,
    private val passwordEncoder: PasswordEncoder,
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        if (userRepository.count() > 0) {
            return
        }

        val workplace = workplaceRepository.save(
            Workplace(name = "PT Manager Cafe", address = "Seoul Gangnam-gu", inviteCode = "CAFE01"),
        )

        val employee = userRepository.save(
            User(
                email = "employee@ptmanager.test",
                password = passwordEncoder.encode("password"),
                name = "Kim Employee",
                role = UserRole.EMPLOYEE,
                workplaceId = workplace.id,
                hourlyWage = 12000,
            ),
        )
        userRepository.save(
            User(
                email = "employer@ptmanager.test",
                password = passwordEncoder.encode("password"),
                name = "Park Employer",
                role = UserRole.EMPLOYER,
                workplaceId = workplace.id,
                hourlyWage = 0,
            ),
        )

        val today = LocalDate.now()
        shiftRepository.save(
            Shift(
                workplaceId = workplace.id!!,
                employeeId = employee.id!!,
                workDate = today,
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(14, 0),
            ),
        )
        shiftRepository.save(
            Shift(
                workplaceId = workplace.id!!,
                employeeId = employee.id!!,
                workDate = today.plusDays(1),
                startTime = LocalTime.of(14, 0),
                endTime = LocalTime.of(20, 0),
            ),
        )
    }
}

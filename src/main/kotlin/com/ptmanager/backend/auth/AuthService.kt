package com.ptmanager.backend.auth

import com.ptmanager.backend.domain.User
import com.ptmanager.backend.repository.UserRepository
import org.springframework.stereotype.Service
import java.util.NoSuchElementException

@Service
class AuthService(
    private val userRepository: UserRepository,
) {

    fun login(email: String, password: String): User {
        require(password == "password") { "Invalid credentials." }
        return userRepository.findByEmailIgnoreCase(email)
            ?: throw NoSuchElementException("User not found.")
    }
}

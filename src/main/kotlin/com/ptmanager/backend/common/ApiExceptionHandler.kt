package com.ptmanager.backend.common

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant
import java.util.NoSuchElementException

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(exception: NoSuchElementException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiError("NOT_FOUND", exception.message, Instant.now(), emptyMap()))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(exception: IllegalArgumentException): ResponseEntity<ApiError> =
        ResponseEntity.badRequest()
            .body(ApiError("BAD_REQUEST", exception.message, Instant.now(), emptyMap()))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(exception: MethodArgumentNotValidException): ResponseEntity<ApiError> {
        val fields = exception.bindingResult.fieldErrors.associate { error ->
            error.field to (error.defaultMessage ?: "invalid")
        }
        return ResponseEntity.badRequest()
            .body(ApiError("VALIDATION_FAILED", "Request validation failed.", Instant.now(), fields))
    }

    data class ApiError(
        val code: String,
        val message: String?,
        val timestamp: Instant,
        val fields: Map<String, String>,
    )
}

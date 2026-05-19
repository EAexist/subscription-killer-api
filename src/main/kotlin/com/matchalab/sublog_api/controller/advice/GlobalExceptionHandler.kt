package com.matchalab.sublog_api.controller.advice

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.matchalab.sublog_api.controller.dto.ErrorResponse
import com.matchalab.sublog_api.exception.GoogleOAuthReAuthRequiredException
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

private val logger = KotlinLogging.logger {}

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(GoogleOAuthReAuthRequiredException::class)
    fun handleGoogleOAuthReAuthRequired(
        ex: GoogleOAuthReAuthRequiredException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn { "Google OAuth re-authentication required: ${ex.message}" }

        val errorResponse = ErrorResponse(
            status = HttpStatus.UNAUTHORIZED.value(),
            error = "REAUTH_REQUIRED",
            message = ex.message ?: "Google OAuth re-authentication required",
            path = request.requestURI
        )

        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(errorResponse)
    }

    @ExceptionHandler(GoogleJsonResponseException::class)
    fun handleGoogleJsonResponseException(
        ex: GoogleJsonResponseException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn { "Google API error: ${ex.details.code} - ${ex.details.message}" }

        // Check for OAuth token expired/invalid errors
        if (ex.details.code == 401 ||
            ex.details.message?.contains("invalid_grant", ignoreCase = true) == true ||
            ex.details.message?.contains("token expired", ignoreCase = true) == true
        ) {

            val errorResponse = ErrorResponse(
                status = HttpStatus.UNAUTHORIZED.value(),
                error = "REAUTH_REQUIRED",
                message = "Google OAuth token expired, please re-authenticate",
                path = request.requestURI
            )

            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(errorResponse)
        }

        // Handle other Google API errors
        val errorResponse = ErrorResponse(
            status = HttpStatus.BAD_GATEWAY.value(),
            error = "GOOGLE_API_ERROR",
            message = "Google API error: ${ex.details.message}",
            path = request.requestURI
        )

        return ResponseEntity
            .status(HttpStatus.BAD_GATEWAY)
            .body(errorResponse)
    }
}

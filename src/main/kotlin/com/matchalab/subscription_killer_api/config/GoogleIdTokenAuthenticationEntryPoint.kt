package com.matchalab.subscription_killer_api.config

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint

//@Component
class GoogleIdTokenAuthenticationEntryPoint(private val objectMapper: ObjectMapper) :
    AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = MediaType.APPLICATION_JSON_VALUE

        val body =
            mapOf(
                "status" to HttpServletResponse.SC_UNAUTHORIZED,
                "error" to "Authentication Failed",
                "message" to authException.message
            )
        response.writer.write(objectMapper.writeValueAsString(body))
        response.writer.flush()
    }
}

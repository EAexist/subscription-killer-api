package com.matchalab.subscription_killer_api.config

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.IOException
import org.springframework.http.MediaType
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper

@Component
class AuthRequestBodyValidationFilter(
        private val authenticationManager: AuthenticationManager,
        private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {

    private val REQUIRED_PATH_PREFIX = "/api/v1/auth"
    private val REQUIRED_METHOD = "POST"

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        return !(request.requestURI.startsWith(REQUIRED_PATH_PREFIX) &&
                request.method.equals(REQUIRED_METHOD, ignoreCase = true))
    }

    override fun doFilterInternal(
            request: HttpServletRequest,
            response: HttpServletResponse,
            filterChain: FilterChain,
    ) {

        logger.debug("AuthRequestBodyValidationFilter.doFilterInternal()")
        val wrappedRequest = ContentCachingRequestWrapper(request)
        wrappedRequest.inputStream.readAllBytes()

        try {
            // To fix body stream issue in WebTestClient
            val bodyBytes = wrappedRequest.contentAsByteArray

            if (bodyBytes.isEmpty()) {
                val message = "❌ Request body is empty."
                sendBadRequestResponse(response, message)
                return
            }

            val idToken: String? = objectMapper.readTree(bodyBytes).get("idToken")?.asText()

            if (idToken == null) {
                val message = "❌ Field 'idToken' is empty."
                sendBadRequestResponse(response, message)
                return
            }
        } catch (e: IOException) {
            logger.error("Error processing request body", e)
            sendBadRequestResponse(response, "Malformed JSON request body or unreadable stream.")
            return
        }

        filterChain.doFilter(wrappedRequest, response)
    }
    private fun sendBadRequestResponse(response: HttpServletResponse, message: String) {

        logger.debug(message)
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST)

        response.setContentType(MediaType.APPLICATION_JSON_VALUE)

        val errorDetails: MutableMap<String, Any> = mutableMapOf()
        errorDetails.put("status", HttpServletResponse.SC_BAD_REQUEST)
        errorDetails.put("error", "Invalid Request Body")
        errorDetails.put("message", message)

        objectMapper.writeValue(response.getOutputStream(), errorDetails)
    }
}

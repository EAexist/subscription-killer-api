package com.matchalab.subscription_killer_api.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.matchalab.subscription_killer_api.domain.UserRoleType
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper

@Component
class GoogleTokenAuthFilter(
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

        logger.debug("GoogleTokenAuthFilter.doFilterInternal()")
        val wrappedRequest = request as ContentCachingRequestWrapper

        try {
            val idToken = extractIdTokenFromRequestBody(wrappedRequest)

            if (idToken.isNullOrBlank()) {
                throw BadCredentialsException("❌ ID Token parameter is missing in request.")
            }

            val authResult =
                    authenticationManager.authenticate(
                            GoogleIdTokenAuthenticationToken(
                                    idToken,
                                    listOf(SimpleGrantedAuthority(UserRoleType.USER.authority))
                            )
                    )

            SecurityContextHolder.getContext().authentication = authResult
            logger.debug("authResult: ${authResult.toString()}")
        } catch (e: Exception) {
            val authException =
                    if (e is AuthenticationException) e
                    else
                            BadCredentialsException(
                                    "❌ Failed to process token request: ${e.message}",
                                    e
                            )
            throw authException
        }

        filterChain.doFilter(wrappedRequest, response)
    }

    private fun extractIdTokenFromRequestBody(
            wrappedRequest: ContentCachingRequestWrapper
    ): String? {

        val bodyBytes = wrappedRequest.contentAsByteArray

        if (bodyBytes.isEmpty()) {
            logger.debug("Request body is empty.")
            return null
        }

        return try {
            objectMapper.readTree(bodyBytes).get("idToken")?.asText()
        } catch (e: Exception) {
            logger.error("❌ Error parsing request body for idToken: ${e.message}")
            null
        }
    }
}

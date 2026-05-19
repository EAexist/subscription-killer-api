package com.matchalab.sublog_api.security.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import java.io.IOException

@Component
class CustomSuccessHandler(
    private val oAuth2Properties: OAuth2Properties
) : SimpleUrlAuthenticationSuccessHandler() {

    @Throws(IOException::class)
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        // If the user was already authenticated, they might be linking an account
        // We can check the session or a flag to decide where to redirect.
        // For now, redirect to the default configured URI.

        val redirectUri = if (authentication.isAuthenticated && authentication !is AnonymousAuthenticationToken) {
            // Logic for linked account redirect can go here
            oAuth2Properties.redirectUri
        } else {
            oAuth2Properties.redirectUri
        }

        redirectStrategy.sendRedirect(request, response, redirectUri)
    }
}

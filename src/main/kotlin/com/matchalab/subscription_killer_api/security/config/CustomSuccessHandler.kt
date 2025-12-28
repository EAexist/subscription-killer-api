package com.matchalab.subscription_killer_api.security.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import java.io.IOException

@Component
class CustomSuccessHandler(
    private val oAuth2Properties: OAuth2Properties
) : SimpleUrlAuthenticationSuccessHandler(

) {
    @Throws(IOException::class)
    public override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        getRedirectStrategy().sendRedirect(request, response, oAuth2Properties.redirectUri)
    }
}
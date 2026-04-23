package com.matchalab.subscription_killer_api.security.config

import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository
import org.springframework.stereotype.Component
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

@Component
class AccountLinkingAuthorizedClientRepository(
    private val delegate: OAuth2AuthorizedClientService
) : OAuth2AuthorizedClientRepository {

    override fun <T : OAuth2AuthorizedClient?> loadAuthorizedClient(
        clientRegistrationId: String,
        principal: Authentication,
        request: HttpServletRequest
    ): T? = delegate.loadAuthorizedClient(clientRegistrationId, principal.name)

    override fun saveAuthorizedClient(
        authorizedClient: OAuth2AuthorizedClient,
        principal: Authentication,
        request: HttpServletRequest,
        response: HttpServletResponse
    ) {
        // If user is already authenticated (and not via 'anonymousUser')
        if (principal.isAuthenticated && principal !is AnonymousAuthenticationToken) {
            delegate.saveAuthorizedClient(authorizedClient, principal)
        }
    }

    override fun removeAuthorizedClient(
        clientRegistrationId: String,
        principal: Authentication,
        request: HttpServletRequest,
        response: HttpServletResponse
    ) = delegate.removeAuthorizedClient(clientRegistrationId, principal.name)
}

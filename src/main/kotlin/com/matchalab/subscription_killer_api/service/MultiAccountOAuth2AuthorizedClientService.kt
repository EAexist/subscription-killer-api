package com.matchalab.subscription_killer_api.service

import com.matchalab.subscription_killer_api.domain.AppUser
import com.matchalab.subscription_killer_api.domain.GoogleAccount
import com.matchalab.subscription_killer_api.repository.GoogleAccountRepository
import com.matchalab.subscription_killer_api.security.CustomOidcUser
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.OAuth2RefreshToken
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

private val logger = KotlinLogging.logger {}

@Profile("google-auth")
@Service
class MultiAccountOAuth2AuthorizedClientService(
    private val clientRegistrationRepository: ClientRegistrationRepository,
    private val googleAccountRepository: GoogleAccountRepository,
    private val appUserService: AppUserService,
) : OAuth2AuthorizedClientService {

    @Transactional
    override fun saveAuthorizedClient(client: OAuth2AuthorizedClient, principal: Authentication) {
        val customUser = principal.principal as CustomOidcUser
        val googleAccountSubject: String = customUser.getGoogleSubject()

        val existingGoogleAccount: GoogleAccount? = googleAccountRepository.findByIdOrNull(googleAccountSubject)

        if (existingGoogleAccount != null) {
            logger.debug { "\uD83D\uDD0A [saveAuthorizedClient] Updating existing GoogleAccount" }
            client.refreshToken?.tokenValue?.let {
                logger.debug { "\uD83D\uDD0A [saveAuthorizedClient] Updating RefreshToken" }
                existingGoogleAccount.refreshToken = it
            }
            existingGoogleAccount.accessToken = client.accessToken.tokenValue
            existingGoogleAccount.expiresAt = client.accessToken.expiresAt
            googleAccountRepository.save(existingGoogleAccount)
        } else {
            logger.debug { "\uD83D\uDD0A [saveAuthorizedClient] Adding new GoogleAccount" }
            logger.debug {
                "\uD83D\uDD0A [saveAuthorizedClient] " +
                        "RefreshToken: ${client.refreshToken?.tokenValue ?: "NULL"}, " +
                        "AccessToken: ${client.accessToken.tokenValue}, " +
                        "ExpiresAt: ${client.accessToken.expiresAt}"
            }
            val attributes = (principal as? OAuth2AuthenticationToken)?.principal?.attributes
                ?: throw (Exception("Principal attributes not found"))
            val name: String = attributes["name"] as String
            val email: String = attributes["email"] as String

            val newGoogleAccount: GoogleAccount = GoogleAccount(
                subject = googleAccountSubject,
                name = name,
                email = email,
                refreshToken = client.refreshToken?.tokenValue,
                accessToken = client.accessToken.tokenValue,
                expiresAt = client.accessToken.expiresAt
            )
            val currentUser = (principal.principal as CustomOidcUser)
            val appUser = appUserService.findByIdOrNotFound(currentUser.appUserId!!)
            appUser.addGoogleAccount(newGoogleAccount)
            appUserService.save(appUser)
        }
    }

    override fun <T : OAuth2AuthorizedClient> loadAuthorizedClient(
        clientRegistrationId: String,
        principalName: String
    ): T? {

        val clientRegistration = clientRegistrationRepository.findByRegistrationId(clientRegistrationId) ?: return null
        var googleAccount: GoogleAccount

        val (appUserIdString, googleAccountSubject) = principalName.split(":", limit = 2)


        if (googleAccountSubject.isNotEmpty()) {
            googleAccount = googleAccountRepository.findByIdOrNull(googleAccountSubject) ?: return null
        } else {
            val appUserId =
                UUID.fromString(appUserIdString)
            val appUser: AppUser = appUserService.findByIdOrNull(
                appUserId
            ) ?: throw IllegalStateException("No Google Account linked for user $appUserId")
            googleAccount = appUser.googleAccounts.first()
        }


        return OAuth2AuthorizedClient(
            clientRegistration,
            principalName,
            OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                googleAccount.accessToken,
                null,
                googleAccount.expiresAt
            ),
            googleAccount.refreshToken?.let {
                OAuth2RefreshToken(it, null)
            }
        ) as T
    }

    override fun removeAuthorizedClient(clientRegistrationId: String, principalName: String) {
        val googleAccountSubject = if (principalName.contains(":")) {
            principalName.split(":")[1]
        } else {
            principalName
        }

        googleAccountRepository.findByIdOrNull(googleAccountSubject)?.let { account ->
            account.accessToken = null
            account.refreshToken = null
            account.expiresAt = null
            googleAccountRepository.save(account)
        }
    }
}
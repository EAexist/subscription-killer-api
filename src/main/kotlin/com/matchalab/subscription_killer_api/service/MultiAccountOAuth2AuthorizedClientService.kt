package com.matchalab.subscription_killer_api.service

import com.matchalab.subscription_killer_api.domain.AppUser
import com.matchalab.subscription_killer_api.domain.GoogleAccount
import com.matchalab.subscription_killer_api.repository.AppUserRepository
import com.matchalab.subscription_killer_api.repository.GoogleAccountRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.OAuth2RefreshToken
import org.springframework.stereotype.Service
import java.util.*

@Service
class MultiAccountOAuth2AuthorizedClientService(
    private val clientRegistrationRepository: ClientRegistrationRepository,
    private val googleAccountRepository: GoogleAccountRepository,
    private val appUserRepository: AppUserRepository,
) : OAuth2AuthorizedClientService {

    override fun saveAuthorizedClient(client: OAuth2AuthorizedClient, principal: Authentication) {
        val googleAccountSubject: String = (client.principalName)

        val googleAccount: GoogleAccount? = googleAccountRepository.findByIdOrNull(googleAccountSubject)
        if (googleAccount != null) {
            googleAccount.refreshToken = client.refreshToken?.tokenValue
            googleAccount.accessToken = client.accessToken.tokenValue
            googleAccount.expiresAt = client.accessToken.expiresAt
            googleAccountRepository.save(googleAccount)
        } else {

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

            val newAppUser: AppUser = AppUser(null, name)
            newAppUser.addGoogleAccount(newGoogleAccount)
            appUserRepository.save(newAppUser)
        }
    }

    override fun <T : OAuth2AuthorizedClient> loadAuthorizedClient(
        clientRegistrationId: String,
        principalName: String
    ): T {

        val clientRegistration = clientRegistrationRepository.findByRegistrationId(clientRegistrationId)
        var googleAccount: GoogleAccount

        val (appUserIdString, googleAccountSubject) = principalName.split(":", limit = 2)


        if (googleAccountSubject.isNotEmpty()) {
            googleAccount = googleAccountRepository.findById(googleAccountSubject).orElseThrow {
                IllegalStateException("No Google Account linked for user $googleAccountSubject")
            }
        } else {
            val appUserId =
                UUID.fromString(appUserIdString)
            val appUser: AppUser = appUserRepository.findByIdOrNull(
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
        val userId = UUID.fromString(principalName)
        appUserRepository.deleteById(userId)
    }
}
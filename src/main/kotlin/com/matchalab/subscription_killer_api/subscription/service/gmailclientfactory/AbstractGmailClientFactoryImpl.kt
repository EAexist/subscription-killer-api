package com.matchalab.subscription_killer_api.subscription.service.gmailclientfactory

import com.google.api.client.auth.oauth2.ClientParametersAuthentication
import com.google.api.client.auth.oauth2.TokenRequest
import com.google.api.client.auth.oauth2.TokenResponse
import com.google.api.client.auth.oauth2.TokenResponseException
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.GenericUrl
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.UserCredentials
import com.matchalab.subscription_killer_api.config.GoogleClientProperties
import com.matchalab.subscription_killer_api.domain.GoogleAccount
import com.matchalab.subscription_killer_api.repository.GoogleAccountRepository
import com.matchalab.subscription_killer_api.subscription.config.MailProperties
import com.matchalab.subscription_killer_api.utils.observe
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.observation.ObservationRegistry
import org.springframework.context.annotation.Profile
import java.time.Instant

private val logger = KotlinLogging.logger {}

@Profile("google-auth && gmail")
abstract class AbstractGmailClientFactoryImpl(
    protected val googleAccountRepository: GoogleAccountRepository,
    protected val googleClientProperties: GoogleClientProperties,
    protected val mailProperties: MailProperties,
    protected val observationRegistry: ObservationRegistry,
) : GmailClientFactory {

    protected val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
    protected val jsonFactory = GsonFactory.getDefaultInstance()

    protected abstract fun isTokenExpiringSoon(account: GoogleAccount): Boolean

    protected fun createClient(subject: String): Gmail {

        return observationRegistry.observe(
            "gmail create_client",
//            parent,
            "google_account.subject" to subject
        ) {
            val googleAccount: GoogleAccount = requestTokenRotationIfNeeded(subject)

            val accessToken: AccessToken = AccessToken(googleAccount.accessToken, null)
            val refreshToken = googleAccount.refreshToken

            val userCredentials: UserCredentials =
                UserCredentials.newBuilder()
                    .setClientId(googleClientProperties.clientId)
                    .setClientSecret(googleClientProperties.clientSecret)
                    .setAccessToken(accessToken)
                    .setRefreshToken(refreshToken)
                    .build()

            Gmail.Builder(httpTransport, jsonFactory, HttpCredentialsAdapter(userCredentials))
                .setApplicationName("Your-Multi-User-Gmail-App")
                .build()
        }
    }

    private fun requestTokenRotationIfNeeded(subject: String): GoogleAccount {
        val account = googleAccountRepository.findById(subject)
            .orElseThrow { IllegalStateException("Account $subject not found") }

        val isExpiringSoon = isTokenExpiringSoon(account)

        if (isExpiringSoon) {
            val tokenResponse = requestTokenRotation(account.refreshToken!!)

            val newRefreshToken = tokenResponse.refreshToken
            if (newRefreshToken != null) {
                account.updateRefreshToken(newRefreshToken)
            }
            val accessToken = tokenResponse.accessToken
            val expiresAt = Instant.now().plusSeconds(tokenResponse.expiresInSeconds)

            account.updateAccessToken(accessToken, expiresAt)
            return googleAccountRepository.save(account)
        }

        return account
    }

    private fun requestTokenRotation(refreshToken: String): TokenResponse {

        val tokenRequest: TokenRequest =
            TokenRequest(
                httpTransport,
                jsonFactory,
                GenericUrl(googleClientProperties.tokenServerUrl),
                "refresh_token"
            )
                .setClientAuthentication(
                    ClientParametersAuthentication(googleClientProperties.clientId, googleClientProperties.clientSecret)
                )
        tokenRequest.set("refresh_token", refreshToken)

        return try {
            tokenRequest.execute()
        } catch (e: TokenResponseException) {
            // This is the "Gold Mine" of debugging info
            logger.error { "❌ Google OAuth Error: ${e.details?.error}" }
            logger.error { "❌ Error Description: ${e.details?.errorDescription}" }
            logger.error { "❌ Full Details: ${e.details}" }
            throw e
        }
    }
}

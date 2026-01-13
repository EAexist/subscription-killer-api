package com.matchalab.subscription_killer_api.subscription.service.gmailclientfactory

import com.google.api.client.auth.oauth2.ClientParametersAuthentication
import com.google.api.client.auth.oauth2.TokenRequest
import com.google.api.client.auth.oauth2.TokenResponse
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
import com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter.GmailClientAdapter
import com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter.GmailClientAdapterImpl
import com.matchalab.subscription_killer_api.utils.observe
import io.micrometer.observation.ObservationRegistry
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Profile("google-auth && gmail")
@Service
class GmailClientFactoryImpl(
    private val googleAccountRepository: GoogleAccountRepository,
    private val googleClientProperties: GoogleClientProperties,
    private val mailProperties: MailProperties,
    private val observationRegistry: ObservationRegistry,
) : GmailClientFactory {
    private val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
    private val jsonFactory = GsonFactory.getDefaultInstance()

    private val adapterCache = ConcurrentHashMap<String, GmailClientAdapter>()

    override fun createAdapter(subject: String): GmailClientAdapter {

        return adapterCache.getOrPut(subject) {
            val authenticatedGmailClient = createClient(subject)
            GmailClientAdapterImpl(authenticatedGmailClient, mailProperties, observationRegistry)
        }
    }

    private fun createClient(subject: String): Gmail {

        val parent = observationRegistry.currentObservation

        return observationRegistry.observe(
            "gmail.createClient",
            parent,
            "googleAccount.subject" to subject
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

        val isExpiringSoon =
            account.expiresAt?.isBefore(Instant.now().plusSeconds(googleClientProperties.tokenRefreshThresholdSeconds))
                ?: true

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

        return tokenRequest.execute()
    }
}
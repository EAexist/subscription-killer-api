package com.matchalab.subscription_killer_api.subscription

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
import com.matchalab.subscription_killer_api.subscription.service.GmailClientAdapter
import com.matchalab.subscription_killer_api.subscription.service.GmailClientAdapterImpl
import org.springframework.stereotype.Component
import java.time.Instant

//@Profile("gcp", "production")
@Component
class GmailClientFactory(
    private val googleAccountRepository: GoogleAccountRepository,
    private val googleClientProperties: GoogleClientProperties,
) {
    private val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val tokenServerUrl = "https://oauth2.googleapis.com/token"

    fun createAdapter(credentialsIdentifier: String): GmailClientAdapter {

        val authenticatedGmailClient = createClient(credentialsIdentifier)

        return GmailClientAdapterImpl(authenticatedGmailClient)
    }

    fun createClient(subject: String): Gmail {
        val googleAccount: GoogleAccount =
            googleAccountRepository.findById(subject).orElseThrow {
                IllegalStateException("Google Account not found for subject=$subject")
            }

        val oldRefreshToken: String =
            googleAccount.refreshToken
                ?: throw IllegalStateException("Refresh Token missing for subject=$subject")

        val tokenResponse: TokenResponse = refreshTokens(oldRefreshToken)

        val newRefreshToken = tokenResponse.refreshToken
        val expireadAt: Instant = Instant.now().plusSeconds(tokenResponse.expiresInSeconds)
        if (newRefreshToken != null && newRefreshToken != oldRefreshToken) {
            //Encryption
            googleAccount.updateRefreshToken(newRefreshToken, expireadAt)
            googleAccountRepository.save(googleAccount)
        }

        val accessToken: AccessToken = AccessToken(tokenResponse.accessToken, null)
        val currentRefreshToken = newRefreshToken ?: oldRefreshToken

        val userCredentials: UserCredentials =
            UserCredentials.newBuilder()
                .setClientId(googleClientProperties.clientId)
                .setClientSecret(googleClientProperties.clientSecret)
                .setAccessToken(accessToken) // 갱신된 Access Token 사용
                .setRefreshToken(currentRefreshToken) // 최신 Refresh Token 사용
                .build()

        return Gmail.Builder(httpTransport, jsonFactory, HttpCredentialsAdapter(userCredentials))
            .setApplicationName("Your-Multi-User-Gmail-App")
            .build()
    }

    /**
     * Refresh Token을 사용하여 Access Token을 갱신하고, 응답에서 새로운 Refresh Token이 있는지 확인합니다.
     * @return 갱신된 Access/Refresh Token을 포함하는 TokenResponse
     */
    fun refreshTokens(refreshToken: String): TokenResponse {

        val tokenRequest: TokenRequest =
            TokenRequest(
                httpTransport,
                jsonFactory,
                GenericUrl(tokenServerUrl),
                "refresh_token"
            )
                .setClientAuthentication(
                    ClientParametersAuthentication(googleClientProperties.clientId, googleClientProperties.clientSecret)
                )
        tokenRequest.set("refresh_token", refreshToken)

        return tokenRequest.execute()
    }
}

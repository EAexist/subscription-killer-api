package com.matchalab.subscription_killer_api.service

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.matchalab.subscription_killer_api.config.GoogleClientProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.Collections
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
@Profile("gcp", "production")
class GoogleIdTokenVerifierService(private val googleClientProperties: GoogleClientProperties) :
        TokenVerifierService {
    override fun verifyToken(idToken: String): GoogleIdToken.Payload? {

        return try {
            val googleIdTokenVerifier =
                    GoogleIdTokenVerifier.Builder(NetHttpTransport(), GsonFactory())
                            .setAudience(
                                    Collections.singletonList(googleClientProperties.webClientId)
                            )
                            .build()

            googleIdTokenVerifier.verify(idToken).payload
        } catch (e: GeneralSecurityException) {
            logger.error(e) { "Failed to verify Google ID Token due to GeneralSecurityException." }
            null
        } catch (e: IOException) {
            logger.error(e) { "Failed to verify Google ID Token due to IOException." }
            null
        } catch (e: Exception) {
            logger.error(e) { "An unexpected error occurred during Google ID Token verification." }
            null
        }
    }
}

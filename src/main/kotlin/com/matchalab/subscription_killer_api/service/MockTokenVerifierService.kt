package com.matchalab.subscription_killer_api.service

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
@Profile("!gcp", "!production")
class MockTokenVerifierService() : TokenVerifierService {
    override fun verifyToken(idToken: String): GoogleIdToken.Payload? {

        return GoogleIdToken.Payload()
    }
}

package com.matchalab.subscription_killer_api.service

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

//@Service
//@Profile("!gcp")
class MockTokenVerifierService() : TokenVerifierService {
    override fun verifyToken(idToken: String): GoogleIdToken.Payload? {

        return GoogleIdToken.Payload()
    }
}

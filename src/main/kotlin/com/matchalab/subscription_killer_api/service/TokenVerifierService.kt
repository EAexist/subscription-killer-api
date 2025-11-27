package com.matchalab.subscription_killer_api.service

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken

interface TokenVerifierService {
    fun verifyToken(idToken: String): GoogleIdToken.Payload
}

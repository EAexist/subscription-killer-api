package com.matchalab.subscription_killer_api.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Instant

@ConfigurationProperties(prefix = "sample-google-account")
data class SampleGoogleAccountProperties(
    val email: String,
    val subject: String? = null,
    val refreshToken: String,
    val accessToken: String,
    val expiresAt: Instant,
    val scope: String,
)

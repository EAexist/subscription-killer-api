package com.matchalab.subscription_killer_api.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Instant

@ConfigurationProperties(prefix = "google.test")
data class GoogleTestUserProperties(
    val subject: String,
    val refreshToken: String,
    val accessToken: String,
    val expiresAt: Instant,
    val scope: String,
)

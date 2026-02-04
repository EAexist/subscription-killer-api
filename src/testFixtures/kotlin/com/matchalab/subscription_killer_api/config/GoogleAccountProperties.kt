package com.matchalab.subscription_killer_api.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Instant

@ConfigurationProperties(prefix = "app.google-account")
data class GoogleAccountProperties(
    val samples: List<SampleGoogleAccountProperties> = emptyList()
)

data class SampleGoogleAccountProperties(
    val email: String,
    val subject: String,
    val refreshToken: String,
    val accessToken: String,
    val expiresAt: Instant,
    val scope: String,
)

package com.matchalab.subscription_killer_api.benchmark

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Profile
import java.time.Instant

@Profile("benchmark")
@ConfigurationProperties(prefix = "app.google-account")
data class BenchmarkGoogleAccountListProperties(
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
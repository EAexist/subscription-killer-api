package com.matchalab.subscription_killer_api.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Profile

@Profile("google-auth")
@ConfigurationProperties(prefix = "app.google")
data class GoogleClientProperties(
    val clientId: String,
    val clientSecret: String,
    val tokenServerUrl: String,
    val tokenRefreshThresholdSeconds: Long
)

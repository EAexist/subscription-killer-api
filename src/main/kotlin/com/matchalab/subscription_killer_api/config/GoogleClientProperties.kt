package com.matchalab.subscription_killer_api.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Profile

@Profile("gcp", "production")
@ConfigurationProperties(prefix = "app.google")
data class GoogleClientProperties(val webClientId: String)

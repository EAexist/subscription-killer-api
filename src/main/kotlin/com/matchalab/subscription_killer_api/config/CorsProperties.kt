package com.matchalab.subscription_killer_api.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.cors")
data class CorsProperties(
        val allowedOrigins: List<String> = emptyList(),
        val allowedMethods: List<String> = emptyList(),
        val maxAge: Long = 0
)

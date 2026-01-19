package com.matchalab.subscription_killer_api.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.guest-app-user")
data class GuestAppUserProperties(
    val subject: String,
    val name: String,
    val email: String,
)
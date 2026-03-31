package com.matchalab.subscription_killer_api.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.util.UUID

@ConfigurationProperties(prefix = "app.guest-app-user")
data class GuestAppUserProperties(
    val id: UUID,
    val name: String,
    val subjects: List<String>,
)
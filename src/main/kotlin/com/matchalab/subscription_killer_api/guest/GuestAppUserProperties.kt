package com.matchalab.subscription_killer_api.guest

import org.springframework.boot.context.properties.ConfigurationProperties
import java.util.*

@ConfigurationProperties(prefix = "app.guest-app-user")
data class GuestAppUserProperties(
    val id: UUID,
    val name: String,
    val subjects: List<String>,
)
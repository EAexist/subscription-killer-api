package com.matchalab.sublog_api.guest

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.guest-app-user")
data class GuestAppUserProperties(
    val name: String,
    val emails: List<String>,
)
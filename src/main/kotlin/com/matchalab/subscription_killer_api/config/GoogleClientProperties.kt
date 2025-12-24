package com.matchalab.subscription_killer_api.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.google")
data class GoogleClientProperties(val clientId: String, val clientSecret: String)

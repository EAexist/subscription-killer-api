package com.matchalab.subscription_killer_api.security.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.oauth2")
data class OAuth2Properties(val redirectUri: String) {}
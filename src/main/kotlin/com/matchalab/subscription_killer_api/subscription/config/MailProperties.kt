package com.matchalab.subscription_killer_api.subscription.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.mail")
data class MailProperties(val analysisMonths: Long, val maxSnippetSize: Int)
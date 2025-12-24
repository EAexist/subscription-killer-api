package com.matchalab.subscription_killer_api.ai.service.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.core.io.Resource

@ConfigurationProperties(prefix = "app.prompts")
data class PromptTemplateProperties(
    val filterAndCategorizeEmails: Resource,
    val generalizeStringPattern: Resource,
    val mergeEmailDetectionRules: Resource,
)
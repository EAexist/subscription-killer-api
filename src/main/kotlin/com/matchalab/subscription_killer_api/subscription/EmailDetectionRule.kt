package com.matchalab.subscription_killer_api.subscription

import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated

data class EmailDetectionRule(
    @Enumerated(EnumType.STRING)
    val eventType: SubscriptionEventType,

    val subjectKeywords: List<String> = emptyList(),
    val subjectRegex: String? = null,

    val snippetKeywords: List<String> = emptyList(),
    val snippetRegex: String? = null
)

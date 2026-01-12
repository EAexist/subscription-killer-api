package com.matchalab.subscription_killer_api.subscription

import com.matchalab.subscription_killer_api.subscription.service.EmailDetectionRuleGenerationDto
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.time.Instant

data class EmailDetectionRule(

    var isActive: Boolean,
    val updatedAt: Instant,

    @Enumerated(EnumType.STRING)
    val eventType: SubscriptionEventType,

    val subjectRegex: String,
    val snippetRegex: String
) {
    companion object {
        fun createActive(generationDto: EmailDetectionRuleGenerationDto, updatedAt: Instant): EmailDetectionRule {
            return EmailDetectionRule(
                true,
                updatedAt,
                generationDto.eventType,
                generationDto.subjectRegex,
                generationDto.snippetRegex
            )
        }
    }
}

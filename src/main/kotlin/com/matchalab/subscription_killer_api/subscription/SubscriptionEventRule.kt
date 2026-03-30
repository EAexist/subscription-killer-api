package com.matchalab.subscription_killer_api.subscription

import com.matchalab.subscription_killer_api.emailtemplate.EmailTemplate
import com.matchalab.subscription_killer_api.subscription.service.SubscriptionEventRuleGenerationDto
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.time.Instant

data class SubscriptionEventRule(

    var isActive: Boolean,
    val updatedAt: Instant,

    @Enumerated(EnumType.STRING)
    val eventType: SubscriptionEventType,

    val template: EmailTemplate,
) {
    companion object {
        fun createActive(generationDto: SubscriptionEventRuleGenerationDto, updatedAt: Instant): SubscriptionEventRule {
            return SubscriptionEventRule(
                true,
                updatedAt,
                generationDto.eventType,
                generationDto.template,
            )
        }
    }
}





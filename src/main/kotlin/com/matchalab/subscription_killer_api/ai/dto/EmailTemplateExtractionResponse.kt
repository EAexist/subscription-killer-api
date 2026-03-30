package com.matchalab.subscription_killer_api.ai.dto

import com.matchalab.subscription_killer_api.emailtemplate.EmailTemplate
import com.matchalab.subscription_killer_api.subscription.SubscriptionEventType
import com.matchalab.subscription_killer_api.subscription.service.SubscriptionEventRuleGenerationDto

data class EmailTemplateExtractionResult(
    val messageId: String,
    val template: EmailTemplate,
)

fun List<EmailTemplateExtractionResult>.toSubscriptionEventRuleGenerationDto(
    emailCategorizationResponse: EmailCategorizationResponse
): List<SubscriptionEventRuleGenerationDto> {
    val idToType = mutableMapOf<String, SubscriptionEventType>().apply {
        emailCategorizationResponse.subsStartMsgIds.forEach {
            put(
                it,
                SubscriptionEventType.SUBSCRIPTION_START
            )
        }
        emailCategorizationResponse.subsCancelMsgIds.forEach {
            put(
                it,
                SubscriptionEventType.SUBSCRIPTION_CANCEL
            )
        }
        emailCategorizationResponse.monthlyMsgIds.forEach {
            put(
                it,
                SubscriptionEventType.MONTHLY_PAYMENT
            )
        }
        emailCategorizationResponse.annualMsgIds.forEach {
            put(
                it,
                SubscriptionEventType.ANNUAL_PAYMENT
            )
        }
    }
    return this
        .map { result ->
            val eventType = idToType[result.messageId]
            SubscriptionEventRuleGenerationDto(
                eventType = eventType!!,
                template = result.template
            )
        }
}
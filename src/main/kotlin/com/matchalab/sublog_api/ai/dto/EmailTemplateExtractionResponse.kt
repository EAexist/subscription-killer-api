package com.matchalab.sublog_api.ai.dto

import com.matchalab.sublog_api.emailtemplate.EmailTemplate
import com.matchalab.sublog_api.subscription.SubscriptionEventType
import com.matchalab.sublog_api.subscription.service.SubscriptionEventRuleGenerationDto

data class EmailTemplateExtractionResult(
    val messageId: String,
    val template: EmailTemplate,
)

fun List<EmailTemplateExtractionResult>.toSubscriptionEventRuleGenerationDto(
    emailCategorizationResponse: EmailCategorizationResponse
): List<SubscriptionEventRuleGenerationDto> {
    val idToType = mutableMapOf<String, SubscriptionEventType>().apply {
        emailCategorizationResponse.subsStartOrPaymentMsgIds.forEach {
            put(
                it,
                SubscriptionEventType.SUBSCRIPTION_START_OR_PAYMENT
            )
        }
        emailCategorizationResponse.subsCancelMsgIds.forEach {
            put(
                it,
                SubscriptionEventType.SUBSCRIPTION_CANCEL
            )
        }
        emailCategorizationResponse.nonSubsMsgIds.forEach {
            put(
                it,
                SubscriptionEventType.NOT_A_SUBSCRIPTION_EMAIL
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
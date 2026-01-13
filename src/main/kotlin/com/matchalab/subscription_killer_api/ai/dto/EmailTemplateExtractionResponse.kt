package com.matchalab.subscription_killer_api.ai.dto

import com.matchalab.subscription_killer_api.subscription.EmailTemplate
import com.matchalab.subscription_killer_api.subscription.SubscriptionEventType
import com.matchalab.subscription_killer_api.subscription.service.EmailDetectionRuleGenerationDto

data class EmailTemplateExtractionResponse(
    val result: List<EmailTemplateExtractionResult> = emptyList()
)

data class EmailTemplateExtractionResult(
    val messageIds: List<String>,
    val template: EmailTemplate,
)

fun EmailTemplateExtractionResponse.toEmailDetectionRuleGenerationDto(
    emailCategorizationResponse: EmailCategorizationResponse
): List<EmailDetectionRuleGenerationDto> {
    val idToType = mutableMapOf<String, SubscriptionEventType>().apply {
        emailCategorizationResponse.subsStartMsgIds?.forEach {
            put(
                it,
                SubscriptionEventType.SUBSCRIPTION_START
            )
        }
        emailCategorizationResponse.subsCancelMsgIds?.forEach {
            put(
                it,
                SubscriptionEventType.SUBSCRIPTION_CANCEL
            )
        }
        emailCategorizationResponse.monthlyMsgIds?.forEach {
            put(
                it,
                SubscriptionEventType.MONTHLY_PAYMENT
            )
        }
        emailCategorizationResponse.annualMsgIds?.forEach {
            put(
                it,
                SubscriptionEventType.ANNUAL_PAYMENT
            )
        }
    }
    return this.result
        .groupBy { result ->
            idToType[result.messageIds.first()]
        }
        .map { (eventType, results) ->
            EmailDetectionRuleGenerationDto(
                eventType = eventType!!,
                template = EmailTemplate(
                    subjectRegex = results.joinToString("|") { "(${it.template.subjectRegex})" },
                    snippetRegex = results.joinToString("|") { "(${it.template.snippetRegex})" }
                )
            )
        }
}
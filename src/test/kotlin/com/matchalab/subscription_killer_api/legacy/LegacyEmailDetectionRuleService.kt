package com.matchalab.subscription_killer_api.subscription.service

import com.matchalab.subscription_killer_api.ai.service.ChatClientService
import com.matchalab.subscription_killer_api.ai.service.config.PromptTemplateProperties
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.subscription.SubscriptionEventType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

data class LegacyGmailMessageSummaryDto(
    val subject: String,
    val snippet: String
)

@Service
@Profile("test")
class LegacyEmailDetectionRuleService(
    private val chatClientService: ChatClientService,
    private val promptTemplateProperties: PromptTemplateProperties,
) {
    fun generateRules(
        messages: List<GmailMessage>
    ): Map<SubscriptionEventType, EmailDetectionRuleGenerationDto> {
        val emails: List<LegacyGmailMessageSummaryDto> = messages.map {
            LegacyGmailMessageSummaryDto(it.subject, it.snippet)
        }

        val categorizedEmails: FilterAndCategorizeEmailsTaskResponse = filterAndCategorizeEmails(emails)
        val proposedRules: UpdateEmailDetectionRulesFromAIResultDto = generalizeStringPattern(categorizedEmails)

        return listOfNotNull(
            proposedRules.paymentStartRule?.let { SubscriptionEventType.PAID_SUBSCRIPTION_START to it },
            proposedRules.paymentCancelRule?.let { SubscriptionEventType.PAID_SUBSCRIPTION_CANCEL to it },
            proposedRules.monthlyPaymentRule?.let { SubscriptionEventType.MONTHLY_PAYMENT to it },
            proposedRules.annualPaymentRule?.let { SubscriptionEventType.ANNUAL_PAYMENT to it }
        ).toMap()
    }

    fun filterAndCategorizeEmails(emails: List<LegacyGmailMessageSummaryDto>): FilterAndCategorizeEmailsTaskResponse =
        chatClientService.call<FilterAndCategorizeEmailsTaskResponse>(
            promptTemplateProperties.filterAndCategorizeEmails,
            mapOf(
                "emails" to emails
            )
        ) { it.param("task_id", "FILTER_AND_CATEGORIZE_EMAILS") }

    fun generalizeStringPattern(categorizedEmails: FilterAndCategorizeEmailsTaskResponse): UpdateEmailDetectionRulesFromAIResultDto =
        chatClientService.call<UpdateEmailDetectionRulesFromAIResultDto>(
            promptTemplateProperties.generalizeStringPattern,
            mapOf("categorizedEmails" to categorizedEmails)
        ) { it.param("task_id", "GENERALIZE_STRING_PATTERN") }
}
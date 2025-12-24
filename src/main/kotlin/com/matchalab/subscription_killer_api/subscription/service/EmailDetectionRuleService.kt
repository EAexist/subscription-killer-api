package com.matchalab.subscription_killer_api.subscription.service

import com.matchalab.subscription_killer_api.ai.service.ChatClientService
import com.matchalab.subscription_killer_api.ai.service.call
import com.matchalab.subscription_killer_api.ai.service.config.PromptTemplateProperties
import com.matchalab.subscription_killer_api.subscription.EmailDetectionRule
import com.matchalab.subscription_killer_api.subscription.EmailSource
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.subscription.SubscriptionEventType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

data class FilterAndCategorizeEmailsTaskResponse(
    val paymentStartMessages: List<GmailMessage>,
    val paymentCancelMessages: List<GmailMessage>,
    val monthlyPaymentMessages: List<GmailMessage>,
    val annualPaymentMessages: List<GmailMessage>,
)

data class GmailMessageSummaryDto(
    val subject: String,
    val snippet: String
)

data class UpdateEmailDetectionRulesFromAIResultDto(
    val paymentStartRule: EmailDetectionRule?,
    val paymentCancelRule: EmailDetectionRule?,
    val monthlyPaymentRule: EmailDetectionRule?,
    val annualPaymentRule: EmailDetectionRule?,
) {}

@Service
class EmailDetectionRuleService(
    private val chatClientService: ChatClientService,
    private val promptTemplateProperties: PromptTemplateProperties,
) {
    fun updateRules(
        emailSource: EmailSource,
        messages: List<GmailMessage>
    ): Map<SubscriptionEventType, EmailDetectionRule> {
        val emails: List<GmailMessageSummaryDto> = messages.map {
            GmailMessageSummaryDto(it.subject, it.snippet)
        }

        val categorizedEmails: FilterAndCategorizeEmailsTaskResponse = filterAndCategorizeEmails(emails)
        val proposedRules: UpdateEmailDetectionRulesFromAIResultDto = generalizeStringPattern(categorizedEmails)
        val mergedEmailDetectionRules: UpdateEmailDetectionRulesFromAIResultDto =
            mergeEmailDetectionRules(emailSource, proposedRules)

        return listOfNotNull(
            mergedEmailDetectionRules.paymentStartRule?.let { SubscriptionEventType.PAID_SUBSCRIPTION_START to it },
            mergedEmailDetectionRules.paymentCancelRule?.let { SubscriptionEventType.PAID_SUBSCRIPTION_CANCEL to it },
            mergedEmailDetectionRules.monthlyPaymentRule?.let { SubscriptionEventType.MONTHLY_PAYMENT to it },
            mergedEmailDetectionRules.annualPaymentRule?.let { SubscriptionEventType.ANNUAL_PAYMENT to it }
        ).toMap()
    }

    fun filterAndCategorizeEmails(emails: List<GmailMessageSummaryDto>): FilterAndCategorizeEmailsTaskResponse =
        chatClientService.call<FilterAndCategorizeEmailsTaskResponse>(
            promptTemplateProperties.filterAndCategorizeEmails,
            mapOf(
                "emails" to emails
            )
        )

    fun generalizeStringPattern(categorizedEmails: FilterAndCategorizeEmailsTaskResponse): UpdateEmailDetectionRulesFromAIResultDto =
        chatClientService.call<UpdateEmailDetectionRulesFromAIResultDto>(
            promptTemplateProperties.generalizeStringPattern,
            mapOf("categorizedEmails" to categorizedEmails)
        )

    fun mergeEmailDetectionRules(
        emailSource: EmailSource,
        proposedRules: UpdateEmailDetectionRulesFromAIResultDto
    ): UpdateEmailDetectionRulesFromAIResultDto =
        chatClientService.call<UpdateEmailDetectionRulesFromAIResultDto>(
            promptTemplateProperties.mergeEmailDetectionRules, mapOf(
                "currentPaymentStartRule" to (emailSource.paymentStartRule ?: "null"),
                "newPaymentStartRule" to (proposedRules.paymentStartRule ?: "null"),
                "currentPaymentCancelRule" to (emailSource.paymentCancelRule ?: "null"),
                "newPaymentCancelRule" to (proposedRules.paymentCancelRule ?: "null"),
                "currentMonthlyPaymentRule" to (emailSource.monthlyPaymentRule ?: "null"),
                "newMonthlyPaymentRule" to (proposedRules.monthlyPaymentRule ?: "null"),
                "currentAnnualPaymentRule" to (emailSource.annualPaymentRule ?: "null"),
                "newAnnualPaymentRule" to (proposedRules.annualPaymentRule ?: "null"),
            )
        )
}
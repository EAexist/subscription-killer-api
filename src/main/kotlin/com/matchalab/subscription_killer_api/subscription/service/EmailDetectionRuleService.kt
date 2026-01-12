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

data class EmailDetectionRuleGenerationDto(
    val eventType: SubscriptionEventType,

    val subjectRegex: String,
    val snippetRegex: String
)


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
    val paymentStartRule: EmailDetectionRuleGenerationDto?,
    val paymentCancelRule: EmailDetectionRuleGenerationDto?,
    val monthlyPaymentRule: EmailDetectionRuleGenerationDto?,
    val annualPaymentRule: EmailDetectionRuleGenerationDto?,
) {}

@Service
class EmailDetectionRuleService(
    private val chatClientService: ChatClientService,
    private val promptTemplateProperties: PromptTemplateProperties,
) {
    fun generateRules(
        emailSource: EmailSource,
        messages: List<GmailMessage>
    ): Map<SubscriptionEventType, EmailDetectionRuleGenerationDto> {


        logger.debug { "[generateRules] \uD83D\uDE80 Generating email detection rule for email: ${messages.first().senderEmail}" }

        val emails: List<GmailMessageSummaryDto> = messages.map {
            GmailMessageSummaryDto(it.subject, it.snippet)
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

    fun matchMessageToEvent(message: GmailMessage, rule: EmailDetectionRule): Boolean {
        val subjectMatch: Boolean = matchRegex(message.subject, rule.subjectRegex)
        val snippetMatch: Boolean = matchRegex(message.snippet, rule.snippetRegex)
        return subjectMatch && snippetMatch
    }

    fun matchMessageToEvent(message: GmailMessage, rule: EmailDetectionRuleGenerationDto): Boolean {
        val subjectMatch: Boolean = matchRegex(message.subject, rule.subjectRegex)
        val snippetMatch: Boolean = matchRegex(message.snippet, rule.snippetRegex)
        return subjectMatch && snippetMatch
    }

    private fun matchRegex(target: String, regex: String): Boolean {
        return regex.toRegex(RegexOption.IGNORE_CASE).containsMatchIn(target)
    }
}
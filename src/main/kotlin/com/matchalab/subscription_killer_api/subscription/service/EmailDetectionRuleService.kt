package com.matchalab.subscription_killer_api.subscription.service

import com.matchalab.subscription_killer_api.ai.dto.EmailCategorizationResponse
import com.matchalab.subscription_killer_api.ai.dto.EmailTemplateExtractionResponse
import com.matchalab.subscription_killer_api.ai.dto.toEmailDetectionRuleGenerationDto
import com.matchalab.subscription_killer_api.ai.dto.toMessages
import com.matchalab.subscription_killer_api.subscription.EmailSource
import com.matchalab.subscription_killer_api.subscription.EmailTemplate
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.subscription.SubscriptionEventType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

data class EmailDetectionRuleGenerationDto(
    val eventType: SubscriptionEventType,
    val template: EmailTemplate,
)

data class GmailMessageSummaryDto(
    val id: String,
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
    private val emailCategorizationPromptService: EmailCategorizationPromptService,
    private val emailTemplateExtractionPromptService: EmailTemplateExtractionPromptService,
) {
    fun generateRules(
        emailSource: EmailSource,
        messages: List<GmailMessage>
    ): Map<SubscriptionEventType, EmailDetectionRuleGenerationDto> {

        logger.debug { "[generateRules] \uD83D\uDE80 Generating email detection rule for email: ${messages.firstOrNull()?.senderEmail ?: "<NO EMAILS>"}" }

        if (messages.isEmpty()) {
            return mapOf()
        }

        val emailCategorizationResponse: EmailCategorizationResponse = emailCategorizationPromptService.run(messages)

        val subscriptionEventMessages = emailCategorizationResponse.toMessages(messages)

        if (subscriptionEventMessages.isEmpty()) {
            return mapOf()
        }

        val emailTemplateExtractionResponse: EmailTemplateExtractionResponse =
            emailTemplateExtractionPromptService.run(subscriptionEventMessages)

        val emailDetectionRuleGenerationDtos: List<EmailDetectionRuleGenerationDto> =
            emailTemplateExtractionResponse.toEmailDetectionRuleGenerationDto(emailCategorizationResponse)

        return emailDetectionRuleGenerationDtos.associateBy { it.eventType }
    }
}
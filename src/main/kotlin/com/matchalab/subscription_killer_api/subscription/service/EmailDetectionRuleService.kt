package com.matchalab.subscription_killer_api.subscription.service

import com.matchalab.subscription_killer_api.ai.dto.EmailCategorizationResponse
import com.matchalab.subscription_killer_api.ai.dto.EmailTemplateExtractionResponse
import com.matchalab.subscription_killer_api.ai.dto.toEmailDetectionRuleGenerationDto
import com.matchalab.subscription_killer_api.ai.dto.toMessages
import com.matchalab.subscription_killer_api.subscription.EmailSource
import com.matchalab.subscription_killer_api.subscription.EmailTemplate
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.subscription.SubscriptionEventType
import com.matchalab.subscription_killer_api.subscription.service.prompt.EmailCategorizationPromptService
import com.matchalab.subscription_killer_api.subscription.service.prompt.EmailTemplateExtractionPromptService
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

@Service
class EmailDetectionRuleService(
    private val emailCategorizationPromptService: EmailCategorizationPromptService,
    private val emailTemplateExtractionPromptService: EmailTemplateExtractionPromptService,
) {
    fun updateEmailDetectionRules(
        emailSourceToMessages: Map<EmailSource, List<GmailMessage>>
    ) {

        // Collect all messages and create ownership mapping
        val allMessages = mutableListOf<GmailMessage>()
        val messageIdToEmailSourceMap = mutableMapOf<String, EmailSource>()

        emailSourceToMessages.forEach { (emailSource, messages) ->
            if (messages.isNotEmpty()) {
                allMessages.addAll(messages)
                messages.forEach { messageIdToEmailSourceMap[it.id] = emailSource }
                logger.debug { "[updateEmailDetectionRules] Added ${messages.size} messages for ${emailSource.targetAddress}" }
            } else {
                logger.debug { "[updateEmailDetectionRules] Skipping ${emailSource.targetAddress} - no messages" }
            }
        }

        if (allMessages.isEmpty()) {
            logger.debug { "[updateEmailDetectionRules] No messages to process" }
            return
        }

        logger.debug { "[updateEmailDetectionRules] Processing total of ${allMessages.size} messages" }

        // Run AI services once for all messages
        val emailCategorizationResponse = emailCategorizationPromptService.run(allMessages)
        val subscriptionEventMessages = emailCategorizationResponse.toMessages(allMessages)

        if (subscriptionEventMessages.isEmpty()) {
            logger.debug { "[updateEmailDetectionRules] No subscription event messages found" }
            return
        }

        logger.debug { "[updateEmailDetectionRules] Found ${subscriptionEventMessages.size} subscription event messages" }

        val emailTemplateExtractionResponse = emailTemplateExtractionPromptService.run(subscriptionEventMessages)

        // Group subscription messages by EmailSource and generate rules
        subscriptionEventMessages.groupBy { messageIdToEmailSourceMap[it.id] }
            .filterKeys { it != null }
            .forEach { (emailSource, sourceSubscriptionMessages) ->
                emailSource!!
                logger.debug { "[updateEmailDetectionRules] Generating rules for ${emailSource.targetAddress} with ${sourceSubscriptionMessages.size} subscription messages" }

                // Filter responses for this EmailSource's messages
                val sourceMessageIds = sourceSubscriptionMessages.map { it.id }.toSet()

                val sourceTemplateExtractionResponse = EmailTemplateExtractionResponse(
                    result = emailTemplateExtractionResponse.result.filter { result ->
                        result.messageId in sourceMessageIds
                    }
                )

                val sourceCategorizationResponse = EmailCategorizationResponse(
                    subsStartMsgIds = emailCategorizationResponse.subsStartMsgIds.filter { it in sourceMessageIds },
                    subsCancelMsgIds = emailCategorizationResponse.subsCancelMsgIds.filter { it in sourceMessageIds },
                    monthlyMsgIds = emailCategorizationResponse.monthlyMsgIds.filter { it in sourceMessageIds },
                    annualMsgIds = emailCategorizationResponse.annualMsgIds.filter { it in sourceMessageIds }
                )

                val sourceEmailDetectionRuleGenerationDtos =
                    sourceTemplateExtractionResponse.toEmailDetectionRuleGenerationDto(sourceCategorizationResponse)

                if (sourceEmailDetectionRuleGenerationDtos.isNotEmpty()) {
                    val generatedRules = sourceEmailDetectionRuleGenerationDtos.associateBy { it.eventType }
                    emailSource.addEmailDetectionRules(generatedRules)

                    logger.debug {
                        "[updateEmailDetectionRules] ✅ Updated ${emailSource.targetAddress} with ${generatedRules.size} rules: ${
                            generatedRules.keys.joinToString(", ") { it.name }
                        }"
                    }
                } else {
                    logger.debug { "[updateEmailDetectionRules] No rules generated for ${emailSource.targetAddress}" }
                }
            }

        logger.debug { "[updateEmailDetectionRules] \u2705 Completed processing all email sources" }
    }

    fun _generateRules(
        messages: List<GmailMessage>
    ): Map<SubscriptionEventType, EmailDetectionRuleGenerationDto> {

        logger.debug { "[generateRules] \uD83D\uDE80 Generating email detection rule for email: ${messages.firstOrNull()?.senderEmail ?: "<NO EMAILS>"}\n\tmessages: ${messages}" }

        if (messages.isEmpty()) {
            return mapOf()
        }

        val emailCategorizationResponse: EmailCategorizationResponse = emailCategorizationPromptService.run(messages)

        val subscriptionEventMessages = emailCategorizationResponse.toMessages(messages)

        logger.debug { "[generateRules] \uD83D\uDE80 ${messages.firstOrNull()?.senderEmail ?: "<NO EMAILS>"}:\n\tsubscriptionEventMessages: ${subscriptionEventMessages}" }

        if (subscriptionEventMessages.isEmpty()) {
            return mapOf()
        }

        val emailTemplateExtractionResponse: EmailTemplateExtractionResponse =
            emailTemplateExtractionPromptService.run(subscriptionEventMessages)

        val emailDetectionRuleGenerationDtos: List<EmailDetectionRuleGenerationDto> =
            emailTemplateExtractionResponse.toEmailDetectionRuleGenerationDto(emailCategorizationResponse)

        logger.debug {
            "[generateRules] \uD83D\uDE80 ${messages.firstOrNull()?.senderEmail ?: "<NO EMAILS>"}:\nemailDetectionRuleGenerationDtos:\n\t${
                emailDetectionRuleGenerationDtos.joinToString(
                    "\n\t"
                )
            }"
        }

        return emailDetectionRuleGenerationDtos.associateBy { it.eventType }
    }
}

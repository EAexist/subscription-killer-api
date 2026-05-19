package com.matchalab.sublog_api.subscription.service

import com.matchalab.sublog_api.ai.dto.EmailCategorizationResponse
import com.matchalab.sublog_api.ai.dto.toMessagesWithSubscriptionEventType
import com.matchalab.sublog_api.ai.dto.toSubscriptionEventRuleGenerationDto
import com.matchalab.sublog_api.ai.service.prompt.emailcategorization.EmailCategorizationPromptService
import com.matchalab.sublog_api.ai.service.prompt.emailtemplateextraction.EmailTemplateExtractionPromptService
import com.matchalab.sublog_api.emailtemplate.EmailTemplate
import com.matchalab.sublog_api.subscription.GmailMessage
import com.matchalab.sublog_api.subscription.SubscriptionEventType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.util.*

private val logger = KotlinLogging.logger {}

data class SubscriptionEventRuleGenerationDto(
    val eventType: SubscriptionEventType,
    val template: EmailTemplate,
)

@Service
class SubscriptionEventRuleService(
    private val emailCategorizationPromptService: EmailCategorizationPromptService,
    private val emailTemplateExtractionPromptService: EmailTemplateExtractionPromptService,
    private val emailSourceService: EmailSourceService
) {

    suspend fun updateSubscriptionEventRules(
        emailSourceIdToMessages: Map<UUID, List<GmailMessage>>
    ) {

        // Collect all messages and create ownership mapping
        val allMessages = mutableListOf<GmailMessage>()
        val messageIdToEmailSourceMap = mutableMapOf<String, UUID>()

        emailSourceIdToMessages.forEach { (emailSourceId, messages) ->
            if (messages.isNotEmpty()) {
                allMessages.addAll(messages)
                messages.forEach { messageIdToEmailSourceMap[it.id] = emailSourceId }
//                logger.debug { "[updateSubscriptionEventRules] Added ${messages.size} messages for ${emailSourceId}" }
            } else {
//                logger.debug { "[updateSubscriptionEventRules] Skipping ${emailSourceId} - no messages" }
            }
        }

        if (allMessages.isEmpty()) {
            logger.debug { "[updateSubscriptionEventRules] No messages to process" }
            return
        }

        logger.debug { "Calling emailCategorizationPromptService with ${allMessages.size} messages" }

        // Run AI services once for all messages
        val emailCategorizationResponse = emailCategorizationPromptService.run(allMessages)
        val messagesWithSubscriptionEventType =
            emailCategorizationResponse.toMessagesWithSubscriptionEventType(allMessages)

        if (messagesWithSubscriptionEventType.isEmpty()) {
            logger.debug { "No subscription event messages found" }
            return
        }

        logger.debug { "Calling emailTemplateExtractionPromptService with ${messagesWithSubscriptionEventType.size} subscription messages" }

        val emailTemplateExtractionResponse =
            emailTemplateExtractionPromptService.run(messagesWithSubscriptionEventType)

        // Group subscription messages by EmailSource and generate rules
        messagesWithSubscriptionEventType.map { it.first }
            .groupBy { messageIdToEmailSourceMap[it.id] }
            .filterKeys { it != null }
            .forEach { (emailSourceId, sourceSubscriptionMessages) ->

                // Filter responses for this EmailSource's messages
                val sourceMessageIds = sourceSubscriptionMessages.map { it.id }.toSet()

                val sourceTemplateExtractionResponse =
                    emailTemplateExtractionResponse.filter { result ->
                        result.messageId in sourceMessageIds
                    }

                val sourceCategorizationResponse = EmailCategorizationResponse(
                    subsStartOrPaymentMsgIds = emailCategorizationResponse.subsStartOrPaymentMsgIds.filter { it in sourceMessageIds },
                    subsCancelMsgIds = emailCategorizationResponse.subsCancelMsgIds.filter { it in sourceMessageIds },
                    nonSubsMsgIds = emailCategorizationResponse.nonSubsMsgIds.filter { it in sourceMessageIds }
                )

                val sourceSubscriptionEventRuleGenerationDtos =
                    sourceTemplateExtractionResponse.toSubscriptionEventRuleGenerationDto(
                        sourceCategorizationResponse
                    )

                if (sourceSubscriptionEventRuleGenerationDtos.isNotEmpty()) {
                    emailSourceService.addSubscriptionEventRules(
                        emailSourceId!!,
                        sourceSubscriptionEventRuleGenerationDtos
                    )
                    logger.debug { "[updateSubscriptionEventRules] Generated rules for ${emailSourceId} with ${sourceSubscriptionMessages.size} subscription messages" }
                } else {
                    logger.debug { "[updateSubscriptionEventRules] No rules generated for ${emailSourceId}" }
                }
            }
//        logger.debug { "[updateSubscriptionEventRules] \u2705 Completed processing all email sources" }
    }
}

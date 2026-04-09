package com.matchalab.subscription_killer_api.subscription.service

import com.matchalab.subscription_killer_api.ai.dto.EmailCategorizationResponse
import com.matchalab.subscription_killer_api.ai.dto.EmailTemplateExtractionResult
import com.matchalab.subscription_killer_api.ai.dto.toMessages
import com.matchalab.subscription_killer_api.ai.dto.toSubscriptionEventRuleGenerationDto
import com.matchalab.subscription_killer_api.emailtemplate.EmailTemplate
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.subscription.SubscriptionEventType
import com.matchalab.subscription_killer_api.ai.service.prompt.emailcategorization.EmailCategorizationPromptService
import com.matchalab.subscription_killer_api.ai.service.prompt.emailtemplateextraction.EmailTemplateExtractionPromptService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.observation.annotation.Observed
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
    fun match(gmailMessage: GmailMessage) {

    }

//    @Observed(name = "updateSubscriptionEventRules")
    fun updateSubscriptionEventRules(
        emailSourceIdToMessages: Map<UUID, List<GmailMessage>>
    ) {

        // Collect all messages and create ownership mapping
        val allMessages = mutableListOf<GmailMessage>()
        val messageIdToEmailSourceMap = mutableMapOf<String, UUID>()

        emailSourceIdToMessages

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
        val subscriptionEventMessages = emailCategorizationResponse.toMessages(allMessages)

        if (subscriptionEventMessages.isEmpty()) {
            logger.debug { "No subscription event messages found" }
            return
        }

        logger.debug { "Calling emailTemplateExtractionPromptService with ${subscriptionEventMessages.size} subscription messages" }

        val emailTemplateExtractionResponse = emailTemplateExtractionPromptService.run(subscriptionEventMessages)

        // Group subscription messages by EmailSource and generate rules
        subscriptionEventMessages.groupBy { messageIdToEmailSourceMap[it.id] }
            .filterKeys { it != null }
            .forEach { (emailSourceId, sourceSubscriptionMessages) ->

                // Filter responses for this EmailSource's messages
                val sourceMessageIds = sourceSubscriptionMessages.map { it.id }.toSet()

                val sourceTemplateExtractionResponse = emailTemplateExtractionResponse.filter { result ->
                        result.messageId in sourceMessageIds
                    }

                val sourceCategorizationResponse = EmailCategorizationResponse(
                    subsStartMsgIds = emailCategorizationResponse.subsStartMsgIds.filter { it in sourceMessageIds },
                    subsCancelMsgIds = emailCategorizationResponse.subsCancelMsgIds.filter { it in sourceMessageIds },
                    monthlyMsgIds = emailCategorizationResponse.monthlyMsgIds.filter { it in sourceMessageIds },
                    annualMsgIds = emailCategorizationResponse.annualMsgIds.filter { it in sourceMessageIds },
                    nonSubsMsgIds = emailCategorizationResponse.nonSubsMsgIds.filter { it in sourceMessageIds }
                )

                val sourceSubscriptionEventRuleGenerationDtos =
                    sourceTemplateExtractionResponse.toSubscriptionEventRuleGenerationDto(sourceCategorizationResponse)

                if (sourceSubscriptionEventRuleGenerationDtos.isNotEmpty()) {
                    emailSourceService.addSubscriptionEventRules(emailSourceId!!, sourceSubscriptionEventRuleGenerationDtos)
                    logger.debug { "[updateSubscriptionEventRules] Generated rules for ${emailSourceId} with ${sourceSubscriptionMessages.size} subscription messages" }
                } else {
                    logger.debug { "[updateSubscriptionEventRules] No rules generated for ${emailSourceId}" }
                }
            }
//        logger.debug { "[updateSubscriptionEventRules] \u2705 Completed processing all email sources" }
    }
}

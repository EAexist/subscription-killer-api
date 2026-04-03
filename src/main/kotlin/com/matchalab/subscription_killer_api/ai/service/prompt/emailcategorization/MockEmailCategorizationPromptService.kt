package com.matchalab.subscription_killer_api.ai.service.prompt.emailcategorization

import com.fasterxml.jackson.databind.ObjectMapper
import com.matchalab.subscription_killer_api.ai.dto.EmailCategorizationResponse
import com.matchalab.subscription_killer_api.ai.service.MockChatResponseService
import com.matchalab.subscription_killer_api.datasets.EmailDatasetProvider
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.subscription.SubscriptionEventType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
@Primary
class MockEmailCategorizationPromptService(
    private val emailCategorizationPromptServiceImpl: EmailCategorizationPromptServiceImpl,
    private val emailDatasetProvider: EmailDatasetProvider,
    private val mockChatResponseService: MockChatResponseService,
    private val objectMapper: ObjectMapper
) : EmailCategorizationPromptService {

    override fun run(messages: List<GmailMessage>): EmailCategorizationResponse {

        emailCategorizationPromptServiceImpl.run(messages)

        val aggregatedMessages: List<GmailMessage> = emailCategorizationPromptServiceImpl.filterRedundantTemplates(messages).map{ it.first }

        val subsStartMsgIndexes = mutableListOf<String>()
        val subsCancelMsgIndexes = mutableListOf<String>()
        val monthlyMsgIndexes = mutableListOf<String>()
        val annualMsgIndexes = mutableListOf<String>()

        val subsStartMsgIds = mutableListOf<String>()
        val subsCancelMsgIds = mutableListOf<String>()
        val monthlyMsgIds = mutableListOf<String>()
        val annualMsgIds = mutableListOf<String>()

        aggregatedMessages.withIndex().forEach { (index, message) ->
            val eventType = emailDatasetProvider.getSubscriptionEventType(message.id)
//            logger.debug { "eventType: ${eventType} senderEmail: ${message.senderEmail} subject: ${message.subject}" }
            when (eventType) {
                SubscriptionEventType.SUBSCRIPTION_START -> { subsStartMsgIndexes.add(index.toString()); subsStartMsgIds.add(message.id) }
                SubscriptionEventType.SUBSCRIPTION_CANCEL -> { subsCancelMsgIndexes.add(index.toString()); subsCancelMsgIds.add(message.id) }
                SubscriptionEventType.MONTHLY_PAYMENT -> { monthlyMsgIndexes.add(index.toString()); monthlyMsgIds.add(message.id) }
                SubscriptionEventType.ANNUAL_PAYMENT -> { annualMsgIndexes.add(index.toString()); annualMsgIds.add(message.id) }
                SubscriptionEventType.NOT_A_SUBSCRIPTION_EMAIL, null -> {
                    // Ignore unknown or uncached message types
                }
            }
        }

        val response = mapOf(
            "M" to monthlyMsgIndexes,
            "A" to annualMsgIndexes,
            "S" to subsStartMsgIndexes,
            "C" to subsCancelMsgIndexes,
        )

        val resultJson = convertResultToJson(response)

        logger.debug { "Subscription messages: ${response.values.flatten().size}/${aggregatedMessages.size}"}

        mockChatResponseService.generateMockChatContext(
            taskName = "categorize_emails",
            prompt = emailCategorizationPromptServiceImpl.getPrompt(),
            params = emailCategorizationPromptServiceImpl.getParams(messages),
            outputFormattingString = "{\"M\":[],\"A\":[],\"S\":[],\"C\":[]}",
            dataCount = emailCategorizationPromptServiceImpl.getDataCount(messages),
            outputString = resultJson
        )

        return EmailCategorizationResponse(
            subsStartMsgIds = subsStartMsgIds,
            subsCancelMsgIds = subsCancelMsgIds,
            monthlyMsgIds = monthlyMsgIds,
            annualMsgIds = annualMsgIds,
        )
    }

    private fun <T> convertResultToJson(result: T): String {
        return objectMapper.writeValueAsString(result)
    }
}


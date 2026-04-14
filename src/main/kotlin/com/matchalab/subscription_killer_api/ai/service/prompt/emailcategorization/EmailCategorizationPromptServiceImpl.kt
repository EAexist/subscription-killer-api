package com.matchalab.subscription_killer_api.ai.service.prompt.emailcategorization

import com.matchalab.subscription_killer_api.ai.dto.EmailCategorizationResponse
import com.matchalab.subscription_killer_api.ai.service.ChatClientService
import com.matchalab.subscription_killer_api.ai.service.config.PromptTemplateProperties
import com.matchalab.subscription_killer_api.ai.toPromptParamString
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

@Service
class EmailCategorizationPromptServiceImpl(
    private val chatClientService: ChatClientService,
    private val promptTemplateProperties: PromptTemplateProperties,
) : EmailCategorizationPromptService {

    override fun run(messages: List<GmailMessage>): EmailCategorizationResponse {

        val aggregatedMessages: List<GmailMessage> = filterRedundantTemplates(messages)

        logger.debug { "[run] ✨ Condensed messages: ${messages.size} -> ${aggregatedMessages.size}" }
        logger.debug { "[run] ✨ Calling chatClient for ${aggregatedMessages.size} messages" }

        return chatClientService.categorizeEmails(
            getPrompt(),
            getParams(messages),
            aggregatedMessages.size
        ).let { response ->
            val selectedIndices = setOf(
                *response["S"].orEmpty().toTypedArray(),
                *response["C"].orEmpty().toTypedArray(),
            )

            fun mapIndicesToIds(indices: List<Int>) = indices.map { aggregatedMessages[it].id }

            EmailCategorizationResponse(
                subsStartOrPaymentMsgIds = mapIndicesToIds(response["S"].orEmpty()),
                subsCancelMsgIds = mapIndicesToIds(response["C"].orEmpty()),
                nonSubsMsgIds = mapIndicesToIds(aggregatedMessages.indices.filter { it !in selectedIndices }),
            )
            }
    }

    fun getParams(messages: List<GmailMessage>): Map<String, String> {
        val aggregatedMessages: List<GmailMessage> = filterRedundantTemplates(messages)
        return mapOf("emails" to aggregatedMessages.withIndex().joinToString("\n") { (index, it) ->
                it.toPromptParamString(
                    index
                )
        })
    }

    fun getPrompt() : String = promptTemplateProperties.filterAndCategorizeEmails.getContentAsString(Charsets.UTF_8).trimIndent()

    fun getDataCount(messages: List<GmailMessage>) : Int = filterRedundantTemplates(messages).size

    fun filterRedundantTemplates(messages: List<GmailMessage>): List<GmailMessage> = messages
        .groupBy { it.subject to it.snippet }
        .map { (_, group) -> group.first()
        }
}
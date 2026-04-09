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

        val aggregatedMessages: List<Pair<GmailMessage, List<Instant>>> = filterRedundantTemplates(messages)

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
                *response["M"].orEmpty().toTypedArray(),
                *response["A"].orEmpty().toTypedArray()
            )

            fun mapIndicesToIds(indices: List<Int>) = indices.map { aggregatedMessages[it].first.id }

            EmailCategorizationResponse(
                subsStartMsgIds = mapIndicesToIds(response["S"].orEmpty()),
                subsCancelMsgIds = mapIndicesToIds(response["C"].orEmpty()),
                monthlyMsgIds = mapIndicesToIds(response["M"].orEmpty()),
                annualMsgIds = mapIndicesToIds(response["A"].orEmpty()),
                nonSubsMsgIds = mapIndicesToIds(aggregatedMessages.indices.filter { it !in selectedIndices }),
            )
            }
    }

    fun getParams(messages: List<GmailMessage>): Map<String, String> {
        val aggregatedMessages: List<Pair<GmailMessage, List<Instant>>> = filterRedundantTemplates(messages)
        return mapOf("emails" to aggregatedMessages.withIndex().joinToString("\n") { (index, it) ->
            val internalDates = it.second

            val dateString = if (internalDates.size >= 2) {
                "|${
                    internalDates
                        .take(2)
                        .map { it.atZone(ZoneId.of("UTC")).toLocalDate() }
                        .distinct()
                        .joinToString(",") { it.format(DateTimeFormatter.ofPattern("yyMMdd")) }
                }"
            } else ""

            "${
                it.first.toPromptParamString(
                    index
                )
            }${dateString}"

        })
    }

    fun getPrompt() : String = promptTemplateProperties.filterAndCategorizeEmails.getContentAsString(Charsets.UTF_8).trimIndent()

    fun getDataCount(messages: List<GmailMessage>) : Int = filterRedundantTemplates(messages).size

    fun filterRedundantTemplates(messages: List<GmailMessage>): List<Pair<GmailMessage, List<Instant>>> = messages
        .groupBy { it.subject to it.snippet }
        .map { (_, group) ->
            Pair(
                group.first(),
                group.map { it.internalDate }.sorted(),
            )
        }
}
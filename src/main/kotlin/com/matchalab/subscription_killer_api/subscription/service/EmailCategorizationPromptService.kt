package com.matchalab.subscription_killer_api.subscription.service

import com.matchalab.subscription_killer_api.ai.dto.EmailCategorizationPromptParams
import com.matchalab.subscription_killer_api.ai.dto.EmailCategorizationResponse
import com.matchalab.subscription_killer_api.ai.service.ChatClientService
import com.matchalab.subscription_killer_api.ai.service.call
import com.matchalab.subscription_killer_api.ai.service.config.PromptTemplateProperties
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.utils.hideDates
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}


data class AggregatedGmailMessage(
    val id: String,
    val subject: String,
    val snippet: String,
    val internalDates: List<Instant>,
) {
    fun toPromptParamString(): String {
        val dateString = if (internalDates.size >= 2) {
            "|${
                internalDates
                    .map { it.atZone(ZoneId.of("UTC")).toLocalDate() }
                    .distinct()
                    .joinToString(",") { it.format(DateTimeFormatter.ofPattern("yyMMdd")) }
            }"
        } else ""

        return "${this.id}|${this.subject}|${this.snippet}${dateString}"
    }
}

@Service
class EmailCategorizationPromptService(
    private val chatClientService: ChatClientService,
    private val promptTemplateProperties: PromptTemplateProperties,
) {

    fun run(messages: List<GmailMessage>): EmailCategorizationResponse {

        val aggregatedMessages: List<AggregatedGmailMessage> = messages.aggregateMessages()

        val promptParams =
            EmailCategorizationPromptParams(aggregatedMessages.joinToString("\n") { it.toPromptParamString() })

        logger.debug { "[run] ✨  ${messages.firstOrNull()?.senderEmail} Condensed messages: ${messages.size} -> ${aggregatedMessages.size}" }
        logger.debug { "[run] ✨  ${messages.firstOrNull()?.senderEmail} Calling chatClient for ${aggregatedMessages.size} messages" }

        return chatClientService.call<Map<String, List<String>>>(
            promptTemplateProperties.filterAndCategorizeEmails,
            mapOf("emails" to promptParams.emails)
        ).let {
            EmailCategorizationResponse(
                subsStartMsgIds = it["S"].orEmpty(),
                subsCancelMsgIds = it["C"].orEmpty(),
                monthlyMsgIds = it["M"].orEmpty(),
                annualMsgIds = it["A"].orEmpty(),
            )
        }
    }

    private fun List<GmailMessage>.aggregateMessages(): List<AggregatedGmailMessage> = this
        .groupBy { it.subject.hideDates() to it.snippet.hideDates() }
        .map { (key, group) ->
            AggregatedGmailMessage(
                id = group.first().id,
                subject = key.first,
                snippet = key.second,
                internalDates = group.map { it.internalDate }.sorted(),
            )
        }
}
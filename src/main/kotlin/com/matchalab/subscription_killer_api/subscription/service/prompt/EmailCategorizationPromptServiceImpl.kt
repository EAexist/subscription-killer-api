package com.matchalab.subscription_killer_api.subscription.service.prompt

import com.matchalab.subscription_killer_api.ai.dto.EmailCategorizationPromptParams
import com.matchalab.subscription_killer_api.ai.dto.EmailCategorizationResponse
import com.matchalab.subscription_killer_api.ai.service.ChatClientService
import com.matchalab.subscription_killer_api.ai.service.call
import com.matchalab.subscription_killer_api.ai.service.config.PromptTemplateProperties
import com.matchalab.subscription_killer_api.ai.toPromptParamString
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.utils.observe
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.observation.ObservationRegistry
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

@Profile("ai")
@Service
class EmailCategorizationPromptServiceImpl(
    private val chatClientService: ChatClientService,
    private val promptTemplateProperties: PromptTemplateProperties,
    private val observationRegistry: ObservationRegistry
) : EmailCategorizationPromptService {

    override fun run(messages: List<GmailMessage>): EmailCategorizationResponse {

        val aggregatedMessages: List<Pair<GmailMessage, List<Instant>>> = messages.filterRedundantTemplates()

        val promptParams =
            EmailCategorizationPromptParams(aggregatedMessages.withIndex().joinToString("\n") { (index, it) ->
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

        logger.debug { "[run] ✨ Condensed messages: ${messages.size} -> ${aggregatedMessages.size}" }
        logger.debug { "[run] ✨ Calling chatClient for ${aggregatedMessages.size} messages" }

        return observationRegistry.observe(
            "prompt_service email_categorization",
            "task" to "email_categorization"
        ) {
            chatClientService.call<Map<String, List<Int>>>(
                promptTemplateProperties.filterAndCategorizeEmails,
                mapOf("emails" to promptParams.emails)
            ).let { response ->
                EmailCategorizationResponse(
                    subsStartMsgIds = response["S"].orEmpty().map { aggregatedMessages[it].first.id },
                    subsCancelMsgIds = response["C"].orEmpty().map { aggregatedMessages[it].first.id },
                    monthlyMsgIds = response["M"].orEmpty().map { aggregatedMessages[it].first.id },
                    annualMsgIds = response["A"].orEmpty().map { aggregatedMessages[it].first.id },
                )
            }
        }
    }

    private fun List<GmailMessage>.filterRedundantTemplates(): List<Pair<GmailMessage, List<Instant>>> = this
        .groupBy { it.subject to it.snippet }
        .map { (_, group) ->
            Pair(
                group.first(),
                group.map { it.internalDate }.sorted(),
            )
        }
}
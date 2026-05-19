package com.matchalab.sublog_api.ai.observation.config

import com.matchalab.sublog_api.ai.observation.JTokkitTokenCountEstimator
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.common.KeyValue
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationFilter
import org.springframework.ai.chat.observation.ChatModelObservationContext
import org.springframework.ai.google.genai.metadata.GoogleGenAiUsage
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/*
* Must Be observationFilter to be applied on both 1.ChatClientServiceImpl and 2.MockChatClientService
* */
@Component
@Order(1)
class TokenMonitoringObservationFilter(val tokenCountEstimator: JTokkitTokenCountEstimator) : ObservationFilter {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun map(context: Observation.Context): Observation.Context {
        if (context is ChatModelObservationContext) {

            logger.debug { "[map] 🔊  Found AdvisorObservationContext Context. Reading Usage Metadata." }

            val response = context.response
            if (response != null) {

                context.name = "app chat_client_service call"

                logger.debug { "[map] 🔊  Creating <app chat_client_service call>" }

                val usage = response.metadata.usage!!
                val model = response.metadata.model ?: "unknown"

                val taskId = context.get<String>("app.ai.task_name") ?: ""
                val template = context.get<String>("app.ai.prompt_template") ?: ""
                val outputFormattingString = context.get<String>("app.ai.output_formatting_string") ?: ""
                val dataCount = context.get<Int>("app.ai.data_count") ?: 1

//                logger.debug { "[map] 🔊  template=$template outputFormattingString=$outputFormattingString dataCount=$dataCount" }

                val totalInputTokens = usage.promptTokens
                val totalOutputTokens = usage.completionTokens

                val instructionTokens = tokenCountEstimator.estimate(template, model)
                val outputFormattingTokens = tokenCountEstimator.estimate(outputFormattingString, model)

                val inputTokenPerItem: Double =
                    if (dataCount > 0) (totalInputTokens - instructionTokens).toDouble() / dataCount else 0.0
                val outputTokensPerItem =
                    if (dataCount > 0) (totalOutputTokens - outputFormattingTokens).toDouble() / dataCount else 0.0


                context.addLowCardinalityKeyValue(
                    KeyValue.of(
                        "app.ai.task_name",
                        taskId
                    )
                )
                context.addHighCardinalityKeyValue(
                    KeyValue.of(
                        "app.ai.data_count",
                        dataCount.toString()
                    )
                )

                context.addLowCardinalityKeyValue(KeyValue.of("gen_ai.request.model", model))
                context.addHighCardinalityKeyValue(
                    KeyValue.of(
                        "gen_ai.usage.input_tokens",
                        totalInputTokens.toString()
                    )
                )
                context.addHighCardinalityKeyValue(
                    KeyValue.of(
                        "gen_ai.usage.output_tokens",
                        totalOutputTokens.toString()
                    )
                )
                context.addHighCardinalityKeyValue(
                    KeyValue.of(
                        "gen_ai.usage.total_tokens",
                        usage.totalTokens.toString()
                    )
                )
                context.addHighCardinalityKeyValue(
                    KeyValue.of(
                        "gen_ai.usage.instruction_tokens",
                        instructionTokens.toString()
                    )
                )
                context.addHighCardinalityKeyValue(
                    KeyValue.of(
                        "gen_ai.usage.input_tokens_per_item",
                        inputTokenPerItem.toString()
                    )
                )
                context.addHighCardinalityKeyValue(
                    KeyValue.of(
                        "gen_ai.usage.output_tokens_per_item",
                        outputTokensPerItem.toString()
                    )
                )

                if (usage is GoogleGenAiUsage) {
                    logger.debug { "[map] 🔊  Found GoogleGenAiUsage. Adding Metadata to Observation" }

                    context.addHighCardinalityKeyValue(
                        KeyValue.of(
                            "gen_ai.usage.thinking_tokens",
                            usage.thoughtsTokenCount.toString()
                        )
                    )
                    context.addHighCardinalityKeyValue(
                        KeyValue.of(
                            "gen_ai.usage.cached_tokens",
                            usage.cachedContentTokenCount.toString()
                        )
                    )
                }
            }
        }
        return context

    }
}

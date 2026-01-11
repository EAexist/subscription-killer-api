package com.matchalab.subscription_killer_api.ai.service.config

import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.common.KeyValue
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationFilter
import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationContext
import org.springframework.ai.google.genai.metadata.GoogleGenAiUsage
import org.springframework.stereotype.Component

@Component
class GeminiTokenMetadataObservationFilter : ObservationFilter {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun map(context: Observation.Context): Observation.Context {
        if (context is AdvisorObservationContext) {

            logger.debug { "[map] 🔊  Found AdvisorObservationContext Context. Reading Usage Metadata." }

            val usage = context.chatClientResponse?.chatResponse?.metadata?.usage

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
        return context
    }
}
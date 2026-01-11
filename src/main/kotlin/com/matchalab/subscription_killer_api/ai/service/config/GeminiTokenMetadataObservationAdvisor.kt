package com.matchalab.subscription_killer_api.ai.service.config

import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.observation.ObservationRegistry
import org.springframework.ai.chat.client.ChatClientRequest
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.advisor.api.CallAdvisor
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain
import org.springframework.ai.google.genai.metadata.GoogleGenAiUsage
import org.springframework.core.Ordered

//@Component
class GeminiTokenMetadataObservationAdvisor(
    private val observationRegistry: ObservationRegistry
) : CallAdvisor {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun getName(): String = "GeminiTokenMetadataObservationAdvisor"

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    override fun adviseCall(
        chatClientRequest: ChatClientRequest,
        callAdvisorChain: CallAdvisorChain
    ): ChatClientResponse {

        val response = callAdvisorChain.nextCall(chatClientRequest)

        logger.debug { "🔊  [adviseCall] Accessing Observation Context" }

        val observation = observationRegistry.currentObservation

        observation?.let { obs ->

            logger.debug { "🔊  [adviseCall] Found Observation Context. Reading Usage Metadata." }

            val usage = response.chatResponse?.metadata?.usage

            if (usage is GoogleGenAiUsage) {
                logger.debug { "🔊  [adviseCall] Found GoogleGenAiUsage. Adding Metadata to Observation" }
                obs.highCardinalityKeyValue("gen_ai.usage.thinking_tokens", usage.thoughtsTokenCount.toString())
                obs.highCardinalityKeyValue("gen_ai.usage.cached_tokens", usage.cachedContentTokenCount.toString())
            }
        }

        return response
    }
}
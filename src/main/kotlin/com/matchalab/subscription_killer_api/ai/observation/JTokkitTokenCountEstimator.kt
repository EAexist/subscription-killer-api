package com.matchalab.subscription_killer_api.ai.observation

import com.knuddels.jtokkit.api.EncodingRegistry
import com.knuddels.jtokkit.api.EncodingType
import org.springframework.stereotype.Component

@Component
class JTokkitTokenCountEstimator(private val registry: EncodingRegistry) {

    fun estimate(text: String, modelName: String): Int {
        val encoding = registry.getEncoding(EncodingType.O200K_BASE)
        val baseCount = encoding.countTokens(text)

        return when {
            // Gemini uses SentencePiece (usually 8-12% more efficient than Tiktoken)
            modelName.contains("gemini", ignoreCase = true) -> (baseCount * 0.9).toInt()

            // OpenAI models are 1:1 with JTokkit/Tiktoken
            modelName.contains("gpt", ignoreCase = true) -> baseCount

            // Llama/Groq often use different tokenizers, a 1.1x safety margin is common
            modelName.contains("llama", ignoreCase = true) -> (baseCount * 1.1).toInt()

            else -> baseCount
        }.coerceAtLeast(1)
    }
}
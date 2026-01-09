package com.matchalab.subscription_killer_api.ai.service

import com.matchalab.subscription_killer_api.ai.service.config.AIProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.google.genai.GoogleGenAiChatOptions
import org.springframework.context.annotation.Profile
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import java.util.function.Consumer

private val logger = KotlinLogging.logger {}

@Service
@Profile("(ai || prod) && !test")
class ChatClientServiceImpl(
    private val chatClientBuilder: ChatClient.Builder,
    private val aiProperties: AIProperties,
) : ChatClientService {

    val chatClient: ChatClient = chatClientBuilder.build()

    private val optimizedOptions = GoogleGenAiChatOptions.builder()
        .maxOutputTokens(aiProperties.maxOutputTokens)
        .thinkingBudget(0)
        .build()

    open override fun <T : Any> call(
        promptTemplateStream: Resource,
        params: Map<String, String>,
        responseType: Class<T>,
        advisorsCustomizer: Consumer<ChatClient.AdvisorSpec>?,
    ): T {

        val optimizedPromptTemplate: String =
            optimizeString(promptTemplateStream.getContentAsString(Charsets.UTF_8))
        val optimizedParams: Map<String, String> = optimizeParams(params)

        if (optimizedParams.toString().length > aiProperties.maxInputTokens) {
            throw IllegalArgumentException("❌  [call] Params size exceeds safety limit")
        }

        return run(
            chatClient.prompt()
                .options(optimizedOptions),
            optimizedPromptTemplate,
            optimizedParams,
            responseType,
            advisorsCustomizer
        )
    }

    fun <T : Any> run(
        requestSpec: ChatClient.ChatClientRequestSpec,
        promptTemplate: String,
        params: Map<String, Any>,
        responseType: Class<T>,
        advisorsCustomizer: Consumer<ChatClient.AdvisorSpec>?,
    ) = runCatching {
        requireNotNull(
            requestSpec.advisors { advisorSpec ->
                advisorsCustomizer?.accept(advisorSpec)
            }
                .user { u ->
                    u.text(promptTemplate)
                    params.forEach { (k, v) -> u.param(k, v) }
                }
                .call()
                .entity(responseType))
    }.onFailure { e ->
        println("❌  [call] Failed: ${e.message}")
    }.getOrThrow()

    private fun optimizeString(string: String): String {
        return string.trimIndent()
            .replace(Regex("(?m)^\\s+$"), "")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun optimizeParams(
        params: Map<String, String>,
    ): Map<String, String> {
        return params.mapValues { (_, value) ->
            optimizeString(value)
        }
    }

//    override fun clearMemory(chatId: String) {
//        chatMemory.clear(chatId)
//    }
}
package com.matchalab.subscription_killer_api.ai.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.google.genai.GoogleGenAiChatOptions
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel
import org.springframework.context.annotation.Profile
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
@Profile("ai")
final class ChatClientServiceImpl(
    private val chatClient: ChatClient,
    private val objectMapper: ObjectMapper
//    private val chatClientBuilder: ChatClient.Builder
) : ChatClientService {

    private val maxPromptPreviewLength = 100
//    private val chatClient: ChatClient = chatClientBuilder.build()

    open override fun <T : Any> call(
        promptTemplateStream: Resource,
        params: Map<String, Any>,
        responseType: Class<T>
    ): T {
        logger.info { "✨  [call]" }

        val promptTemplate: String = promptTemplateStream.getContentAsString(Charsets.UTF_8).trimIndent()
        val promptPreview = promptTemplate.replace(Regex("\\s+"), " ").take(maxPromptPreviewLength)

        return runCatching {
            val json = chatClient.prompt()
                .options(
                    GoogleGenAiChatOptions.builder().thinkingLevel(GoogleGenAiThinkingLevel.LOW)
                        .includeThoughts(false).responseMimeType("application/json").build()
                )
//                    .advisors { AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT }
                .user { u ->
                    u.text(promptTemplate)
                    params.forEach { (k, v) -> u.param(k, v) }
                }
                .call().content()

            objectMapper.readValue(json, responseType)
        }.onSuccess { entity ->
            val entityString = entity.toString()
            logger.info { "✨  [call] Result\n\tprompt: $promptTemplate\n\tresult: $entityString" }
        }.onFailure { e ->
            println("❌  [call] Failed: ${e.message}")
        }.getOrThrow()
    }

//    override fun <T : Any> call(
//        promptTemplateStream: Resource,
//        params: Map<String, Any>,
//        typeRef: ParameterizedTypeReference<T>
//    ): T {
//
//        val promptTemplate: String = promptTemplateStream.getContentAsString(Charsets.UTF_8).trimIndent()
//
//        return runCatching {
//            val response: T? = chatClient.prompt()
//                .user { u ->
//                    u.text(promptTemplate)
//                    params.forEach { (k, v) -> u.param(k, v) }
//                }
//                .call().entity<T>(typeRef)
//
//            logger.debug { "responseJson: ${response.toString()}" }
//
//            requireNotNull(
//                response
//            )
//        }.onFailure { e ->
//            println("AI Call Failed: ${e.message}")
//        }.getOrThrow()
//    }
}
package com.matchalab.subscription_killer_api.ai.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.observation.annotation.Observed
import org.springframework.ai.chat.client.ChatClient
import org.springframework.context.annotation.Profile
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
@Profile("ai || prod")
class ChatClientServiceImpl(
    private val chatClientBuilder: ChatClient.Builder
) : ChatClientService {

    private val maxPromptPreviewLength = 100
    private val chatClient: ChatClient = chatClientBuilder.build()

    @Observed
    open override fun <T : Any> call(
        promptTemplateStream: Resource,
        params: Map<String, Any>,
        responseType: Class<T>
    ): T {

        val promptTemplate: String = promptTemplateStream.getContentAsString(Charsets.UTF_8).trimIndent()
        val promptPreview = promptTemplate.replace(Regex("\\s+"), " ").take(maxPromptPreviewLength)

        return runCatching {
            requireNotNull(
                chatClient.prompt()
                    .user { u ->
                        u.text(promptTemplate)
                        params.forEach { (k, v) -> u.param(k, v) }
                    }
                    .call()
                    .entity(responseType))
        }.onSuccess { entity ->
            val entityString = entity.toString()
            logger.info { "✨ [Call Result]\n    prompt: $promptPreview,\n    result: $entityString" }
        }.onFailure { e ->
            println("AI Call Failed: ${e.message}")
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
package com.matchalab.subscription_killer_api.ai.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.client.ChatClient
import org.springframework.core.ParameterizedTypeReference
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class ChatClientServiceImpl(
    private val chatClientBuilder: ChatClient.Builder
) : ChatClientService {

    private val chatClient: ChatClient = chatClientBuilder.build()

    override fun <T : Any> call(
        promptTemplateStream: Resource,
        params: Map<String, Any>,
        responseType: Class<T>
    ): T {

        val promptTemplate: String = promptTemplateStream.getContentAsString(Charsets.UTF_8).trimIndent()

        return runCatching {
            requireNotNull(
                chatClient.prompt()
                    .user { u ->
                        u.text(promptTemplate)
                        params.forEach { (k, v) -> u.param(k, v) }
                    }
                    .call()
                    .entity(responseType))
        }.onFailure { e ->
            println("AI Call Failed: ${e.message}")
        }.getOrThrow()
    }

    override fun <T : Any> call(
        promptTemplateStream: Resource,
        params: Map<String, Any>,
        typeRef: ParameterizedTypeReference<T>
    ): T {

        val promptTemplate: String = promptTemplateStream.getContentAsString(Charsets.UTF_8).trimIndent()

        return runCatching {
            val response: T? = chatClient.prompt()
                .user { u ->
                    u.text(promptTemplate)
                    params.forEach { (k, v) -> u.param(k, v) }
                }
                .call().entity<T>(typeRef)

            logger.debug { "responseJson: ${response.toString()}" }

            requireNotNull(
                response
            )
        }.onFailure { e ->
            println("AI Call Failed: ${e.message}")
        }.getOrThrow()
    }
}
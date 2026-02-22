package com.matchalab.subscription_killer_api.ai.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.matchalab.subscription_killer_api.ai.observation.AiObservationUtility
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.ai.google.genai.GoogleGenAiChatOptions
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
@Profile("ai")
final class ChatClientServiceImpl(
    private val chatClient: ChatClient,
    private val objectMapper: ObjectMapper,
    private val aiObservationUtility: AiObservationUtility
) : ChatClientService {

    override fun categorizeEmails(
        prompt: String,
        params: Map<String, String>,
        dataCount: Int?
    ): Map<String, List<Int>> {
        return call(
            "categorize_emails",
            prompt,
            params,
            dataCount
        )
    }

    override fun extractEmailTemplates(
        prompt: String,
        params: Map<String, String>,
        dataCount: Int?
    ): ExtractEmailTemplatesResponse {
        return call(
            "extract_email_templates",
            prompt,
            params,
            dataCount
        )
    }

    private fun <T : Any> call(
        taskName: String,
        prompt: String,
        params: Map<String, String>,
        responseType: Class<T>,
        dataCount: Int?
    ): T {

        val renderedPrompt: String = PromptTemplate(prompt).render(params)

        return aiObservationUtility.observeChatClientServiceCall(
            taskName = taskName,
            template = renderedPrompt,
            outputFormattingString = "",
            dataCount,
        ) { context ->
            runCatching {
                val chatResponse = chatClient.prompt()
                    .options(
                        GoogleGenAiChatOptions.builder()
//                        .thinkingLevel(GoogleGenAiThinkingLevel.LOW)
                            .includeThoughts(false)
                            .responseMimeType("application/json")
                            .build()
                    )
                    .user { u ->
                        u.text(prompt)
                        params.forEach { (k, v) -> u.param(k, v) }
                    }
                    .call()

                val json = chatResponse.content()
                val result = objectMapper.readValue(json, responseType)

                result
            }.onSuccess { entity ->
                val entityString = entity.toString()
                logger.info { "[call] Result\n\tprompt: $prompt\n\tresult: $entityString" }
            }.onFailure { e ->
                println("[call] Failed: ${e.message}")
            }.getOrThrow()
        }
    }

    private inline fun <reified T : Any> call(
        taskName: String,
        prompt: String,
        params: Map<String, String> = emptyMap(),
        dataCount: Int?
    ): T = call(taskName, prompt, params, T::class.java, dataCount)
}

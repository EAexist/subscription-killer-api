package com.matchalab.sublog_api.ai.service

import com.matchalab.sublog_api.ai.observation.AiObservationUtility
import com.matchalab.sublog_api.ai.observation.JTokkitTokenCountEstimator
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.metadata.DefaultUsage
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.observation.ChatModelObservationContext
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.stereotype.Service
import java.util.function.Supplier

@Service
class MockChatResponseService(
    private val aiObservationUtility: AiObservationUtility,
    private val tokenCountEstimator: JTokkitTokenCountEstimator,
    private val observationRegistry: ObservationRegistry
) {

    private fun createMockChatResponse(
        model: String,
        promptString: String,
        outputString: String
    ): ChatResponse {

        val promptTokens = tokenCountEstimator.estimate(promptString, model)
        val completionTokens = tokenCountEstimator.estimate(outputString, model)
        val totalTokens = promptTokens + completionTokens

        val usage = DefaultUsage(
            promptTokens,
            completionTokens,
            totalTokens
        )
        val metadata = ChatResponseMetadata.builder()
            .usage(usage)
            .model(model)
            .build()

        return ChatResponse(listOf(), metadata)
    }

    fun generateMockChatContext(
        taskName: String,
        prompt: String,
        params: Map<String, String>,
        outputFormattingString: String,
        dataCount: Int?,
        outputString: String
    ) {

        val renderedPromptTemplate: String = PromptTemplate(prompt).render(params.mapValues { "" })
        val renderedPrompt: String = PromptTemplate(prompt).render(params)

        // Use observeChatClientServiceCall for context setup (no redundancy)
        var context: ChatModelObservationContext? = null
        aiObservationUtility.observeChatClientServiceCall(
            taskName = taskName,
            template = renderedPromptTemplate,
            outputFormattingString = outputFormattingString,
            dataCount = dataCount
        ) { ctx ->
            context = ctx
            Unit // Return Unit since we're just capturing the context
        }

        // Create and start the observation directly with the context from observeChatClientServiceCall
        Observation.createNotStarted("app chat_client_service call", { context!! }, observationRegistry)
            .observe(Supplier {
                val mockResponse = createMockChatResponse("mock-model", renderedPrompt, outputString)

                // CRITICAL FOR MOCKS: If the block returns a ChatResponse,
                // manually attach it to the context so the Filter can see it.
                context!!.setResponse(mockResponse)

                mockResponse
            })
    }
}

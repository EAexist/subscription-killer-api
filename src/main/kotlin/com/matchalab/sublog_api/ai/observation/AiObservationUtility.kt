package com.matchalab.sublog_api.ai.observation

import io.micrometer.observation.ObservationRegistry
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.observation.ChatModelObservationContext
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.stereotype.Component
import java.util.*

@Component
class AiObservationUtility(
    private val observationRegistry: ObservationRegistry,
    private val chatModel: Optional<ChatModel>
) {

    fun <T> observeChatClientServiceCall(
        taskName: String,
        template: String,
        outputFormattingString: String,
        dataCount: Int? = 1,
        block: (ChatModelObservationContext) -> T,
    ): T {

        val model = chatModel
            .map { it.defaultOptions?.model }
            .orElse("mock-model") ?: "unknown-model"

        val context = ChatModelObservationContext.builder()
            .prompt(Prompt(template))
            .provider(model)
            .build()

        context.put("app.ai.task_name", taskName)
        context.put("app.ai.prompt_template", template)
        context.put("app.ai.data_count", dataCount ?: 1)
        context.put("app.ai.output_formatting_string", outputFormattingString)

        return block(context)
    }
}
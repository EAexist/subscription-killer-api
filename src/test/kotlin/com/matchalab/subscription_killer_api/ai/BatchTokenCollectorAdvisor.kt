package com.matchalab.subscription_killer_api.ai.service.config

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.client.ChatClientMessageAggregator
import org.springframework.ai.chat.client.ChatClientRequest
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.advisor.api.CallAdvisor
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain
import org.springframework.ai.google.genai.metadata.GoogleGenAiUsage
import org.springframework.core.Ordered
import reactor.core.publisher.Flux
import java.util.function.Consumer

private val logger = KotlinLogging.logger {}

data class TokenMetrics(
    val taskId: String,
    val prompt: Int,
    val completion: Int,
    val thinking: Int,
    val cached: Int,
    val total: Int
)

class BatchTokenCollectorAdvisor(
    private val metricsList: MutableList<TokenMetrics>
) : CallAdvisor, StreamAdvisor {

    override fun getName(): String = "TokenMonitoringAdvisor"

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    override fun adviseCall(
        chatClientRequest: ChatClientRequest,
        callAdvisorChain: CallAdvisorChain
    ): ChatClientResponse {

        val response = callAdvisorChain.nextCall(chatClientRequest)

        logger.debug { "✨  [adviseCall] Called BatchTokenCollectorAdvisor" }

        val usage = response.chatResponse()?.metadata?.usage

        logger.debug { "✨  [adviseCall] usage:${usage}, usage is GoogleGenAiUsage:${usage is GoogleGenAiUsage}" }

        if (usage is GoogleGenAiUsage) {

            val taskId = chatClientRequest.context()["task_id"]?.toString() ?: "unknown"

            synchronized(metricsList) {
                metricsList.add(
                    TokenMetrics(
                        taskId = taskId,
                        prompt = usage.promptTokens,
                        completion = usage.completionTokens,
                        thinking = usage.thoughtsTokenCount ?: 0,
                        cached = usage.cachedContentTokenCount ?: 0,
                        total = usage.totalTokens
                    )
                )
            }
        }

        return response
    }

    override fun adviseStream(
        chatClientRequest: ChatClientRequest,
        streamAdvisorChain: StreamAdvisorChain
    ): Flux<ChatClientResponse> {
        val chatClientResponses: Flux<ChatClientResponse> = streamAdvisorChain.nextStream(chatClientRequest)
        return ChatClientMessageAggregator().aggregateChatClientResponse(
            chatClientResponses,
            Consumer { chatClientResponse: ChatClientResponse ->

                logger.debug { "✨  [adviseStream]" }

                val usage = chatClientResponse.chatResponse()?.metadata?.usage

                logger.debug { "✨  [adviseStream] usage:${usage}" }
//
//                if (usage is GoogleGenAiUsage) {
//                    val taskId = chatClientRequest.context()["task_id"]?.toString() ?: "unknown"
//
//                    synchronized(metricsList) {
//                        metricsList.add(
//                            TokenMetrics(
//                                taskId = taskId,
//                                prompt = usage.promptTokens,
//                                completion = usage.completionTokens,
//                                thinking = usage.thoughtsTokenCount ?: 0,
//                                cached = usage.cachedContentTokenCount ?: 0,
//                                total = usage.totalTokens
//                            )
//                        )
//                    }
//                }

            })
    }
}

fun printUsageTable(metrics: List<TokenMetrics>, title: String? = null) {
    val header = "%-30s | %-8s | %-8s | %-8s | %-8s | %-8s".format(
        "Task ID", "Prompt", "Cached", "Thinking", "Compl.", "Total"
    )

    val separator = "-".repeat(header.length)

    StringBuilder().apply {
        appendLine("\n📊 --- TOKEN USAGE EVALUATION REPORT${title?.let { " [$title]" }} ---")
        appendLine(header)
        appendLine(separator)

        metrics.forEach { m ->
            appendLine(
                "%-30s | %-8d | %-8d | %-8d | %-8d | %-8d".format(
                    m.taskId.take(30),
                    m.prompt,
                    m.cached,
                    m.thinking,
                    m.completion,
                    m.total
                )
            )
        }
        appendLine(separator)
    }.also { logger.debug { it.toString() } }
}
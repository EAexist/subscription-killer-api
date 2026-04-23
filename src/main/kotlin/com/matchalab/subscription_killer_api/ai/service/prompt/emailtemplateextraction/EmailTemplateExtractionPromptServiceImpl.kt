package com.matchalab.subscription_killer_api.ai.service.prompt.emailtemplateextraction

import com.matchalab.subscription_killer_api.ai.dto.EmailTemplateExtractionResult
import com.matchalab.subscription_killer_api.ai.service.ChatClientService
import com.matchalab.subscription_killer_api.ai.service.config.PromptTemplateProperties
import com.matchalab.subscription_killer_api.emailtemplate.EmailTemplate
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.subscription.SubscriptionEventType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class EmailTemplateExtractionPromptServiceImpl(
    private val chatClientService: ChatClientService,
    private val promptTemplateProperties: PromptTemplateProperties,
) : EmailTemplateExtractionPromptService {

    override fun run(messagesWithSubscriptionEventType: List<Pair<GmailMessage, SubscriptionEventType>>): List<EmailTemplateExtractionResult> {

        val messages = messagesWithSubscriptionEventType.map { it.first }
        logger.debug { "[run] ✨  Calling chatClient for ${messages.size} messages" }


        return chatClientService.extractEmailTemplates(
            getPrompt(),
            getParams(messagesWithSubscriptionEventType),
            messages.size
        ).let { response ->
            response.result.map { result ->
//                val jList = when (result.j) {
//                    is String -> listOf(result.j.toString().trim())
//                    is List<*> -> (result.j as List<*>).map { it.toString().trim() }
//                    else -> emptyList()
//                }
                EmailTemplateExtractionResult(
                    messages[result.m].id,
                    EmailTemplate(
                        result.j.map { it.trim() },
                        result.p.map { it.trim() }
                    )
                )
            }
        }
    }

    fun getParams(messagesWithSubscriptionEventType: List<Pair<GmailMessage, SubscriptionEventType>>): Map<String, String> {
        return mapOf(
            "emails" to messagesWithSubscriptionEventType.withIndex()
                .joinToString("\n") { (index, it) ->
                    val eventType = when (it.second) {
                        SubscriptionEventType.NOT_A_SUBSCRIPTION_EMAIL -> "N"
                        SubscriptionEventType.SUBSCRIPTION_START_OR_PAYMENT -> "S"
                        SubscriptionEventType.SUBSCRIPTION_CANCEL -> "C"
                    }
                    "${index}|${it.first.subject}|${it.first.snippet}|${eventType}"
                })
    }

    fun getPrompt(): String =
        promptTemplateProperties.generalizeStringPattern.getContentAsString(Charsets.UTF_8)
            .trimIndent()

}
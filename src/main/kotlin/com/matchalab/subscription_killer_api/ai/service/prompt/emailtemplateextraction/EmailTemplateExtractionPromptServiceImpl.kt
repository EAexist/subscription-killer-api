package com.matchalab.subscription_killer_api.ai.service.prompt.emailtemplateextraction

import com.matchalab.subscription_killer_api.ai.dto.EmailTemplateExtractionResult
import com.matchalab.subscription_killer_api.ai.service.ChatClientService
import com.matchalab.subscription_killer_api.ai.service.config.PromptTemplateProperties
import com.matchalab.subscription_killer_api.ai.toPromptParamString
import com.matchalab.subscription_killer_api.emailtemplate.EmailTemplate
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import kotlin.collections.mapOf

private val logger = KotlinLogging.logger {}

@Service
class EmailTemplateExtractionPromptServiceImpl(
    private val chatClientService: ChatClientService,
    private val promptTemplateProperties: PromptTemplateProperties,
): EmailTemplateExtractionPromptService {

    override fun run(messages: List<GmailMessage>): List<EmailTemplateExtractionResult> {
        logger.debug { "[run] ✨  Calling chatClient for ${messages.size} messages" }

        return chatClientService.extractEmailTemplates(
            getPrompt(),
            getParams(messages),
            messages.size
        ).let { response -> response.result.map { result ->
                    EmailTemplateExtractionResult(
                        messages[result.m].id,
                        EmailTemplate(
                            result.j.map{it.trim()},
                            result.p.map{it.trim()}
                        )
                    )
                }
        }
    }

    fun getParams(messages: List<GmailMessage>): Map<String, String> {
        return mapOf("emails" to messages.withIndex().joinToString("\n") { (index, it) ->
            it.toPromptParamString(
                index
            )
        })
    }

    fun getPrompt() : String = promptTemplateProperties.generalizeStringPattern.getContentAsString(Charsets.UTF_8).trimIndent()

}
package com.matchalab.subscription_killer_api.subscription.service

import com.matchalab.subscription_killer_api.ai.dto.EmailTemplateExtractionPromptParams
import com.matchalab.subscription_killer_api.ai.dto.EmailTemplateExtractionResponse
import com.matchalab.subscription_killer_api.ai.service.ChatClientService
import com.matchalab.subscription_killer_api.ai.service.call
import com.matchalab.subscription_killer_api.ai.service.config.PromptTemplateProperties
import com.matchalab.subscription_killer_api.ai.toPromptParamString
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.utils.hideDates
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class EmailTemplateExtractionPromptService(
    private val chatClientService: ChatClientService,
    private val promptTemplateProperties: PromptTemplateProperties,
) {

    fun run(messages: List<GmailMessage>): EmailTemplateExtractionResponse {

        val uniqueMessages = messages.distinctBy { it.subject.hideDates() to it.snippet.hideDates() }
        val promptParams =
            EmailTemplateExtractionPromptParams(uniqueMessages.joinToString("\n") { it.toPromptParamString() })

        logger.debug { "[run] ✨  Condensed messages: ${messages.size} -> ${uniqueMessages.size}" }
        logger.debug { "[run] ✨  Calling chatClient for ${uniqueMessages.size} messages" }

        return chatClientService.call<EmailTemplateExtractionResponse>(
            promptTemplateProperties.generalizeStringPattern,
            mapOf("emails" to promptParams.emails)
        )
    }
}
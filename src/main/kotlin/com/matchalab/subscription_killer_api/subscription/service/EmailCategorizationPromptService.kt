package com.matchalab.subscription_killer_api.subscription.service

import com.matchalab.subscription_killer_api.ai.dto.EmailCategorizationPromptParams
import com.matchalab.subscription_killer_api.ai.dto.EmailCategorizationResponse
import com.matchalab.subscription_killer_api.ai.service.ChatClientService
import com.matchalab.subscription_killer_api.ai.service.call
import com.matchalab.subscription_killer_api.ai.service.config.PromptTemplateProperties
import com.matchalab.subscription_killer_api.ai.toPromptParamString
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class EmailCategorizationPromptService(
    private val chatClientService: ChatClientService,
    private val promptTemplateProperties: PromptTemplateProperties,
) {

    fun run(messages: List<GmailMessage>): EmailCategorizationResponse {

        val uniqueMessages = messages.distinctBy { it.subject to it.snippet }
        val promptParams =
            EmailCategorizationPromptParams(uniqueMessages.joinToString("\n") { it.toPromptParamString() })

        logger.debug { "[run] ✨  Condensed messages: ${messages.size} -> ${uniqueMessages.size}" }
        logger.debug { "[run] ✨  Calling chatClient for ${uniqueMessages.size} messages" }

        return chatClientService.call<EmailCategorizationResponse>(
            promptTemplateProperties.filterAndCategorizeEmails,
            mapOf("emails" to promptParams.emails)
        )
    }
}
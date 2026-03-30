package com.matchalab.subscription_killer_api.ai.service.prompt.emailcategorization

import com.matchalab.subscription_killer_api.ai.dto.EmailCategorizationResponse
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

interface EmailCategorizationPromptService {
    fun run(messages: List<GmailMessage>): EmailCategorizationResponse
}
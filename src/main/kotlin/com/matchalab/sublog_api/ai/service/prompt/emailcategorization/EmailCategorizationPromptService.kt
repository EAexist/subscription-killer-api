package com.matchalab.sublog_api.ai.service.prompt.emailcategorization

import com.matchalab.sublog_api.ai.dto.EmailCategorizationResponse
import com.matchalab.sublog_api.subscription.GmailMessage
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

interface EmailCategorizationPromptService {
    fun run(messages: List<GmailMessage>): EmailCategorizationResponse
}
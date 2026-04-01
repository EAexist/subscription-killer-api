package com.matchalab.subscription_killer_api.ai.service.prompt.emailtemplateextraction

import com.matchalab.subscription_killer_api.ai.dto.EmailTemplateExtractionResult
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

interface EmailTemplateExtractionPromptService {
    fun run(messages: List<GmailMessage>): List<EmailTemplateExtractionResult>
}
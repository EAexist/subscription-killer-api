package com.matchalab.sublog_api.ai.service.prompt.emailtemplateextraction

import com.matchalab.sublog_api.ai.dto.EmailTemplateExtractionResult
import com.matchalab.sublog_api.subscription.GmailMessage
import com.matchalab.sublog_api.subscription.SubscriptionEventType
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

interface EmailTemplateExtractionPromptService {
    fun run(messagesWithSubscriptionEventType: List<Pair<GmailMessage, SubscriptionEventType>>): List<EmailTemplateExtractionResult>
}
package com.matchalab.subscription_killer_api.subscription.service.prompt

import com.matchalab.subscription_killer_api.ai.dto.EmailTemplateExtractionResponse
import com.matchalab.subscription_killer_api.ai.dto.EmailTemplateExtractionResult
import com.matchalab.subscription_killer_api.subscription.EmailTemplate
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Profile("!ai")
@Service
class MockEmailTemplateExtractionPromptService(
) : EmailTemplateExtractionPromptService {
    override fun run(messages: List<GmailMessage>): EmailTemplateExtractionResponse {

        return EmailTemplateExtractionResponse(
            result = messages.map {
                EmailTemplateExtractionResult(
                    it.id,
                    EmailTemplate(
                        it.subject,
                        it.snippet
                    )
                )
            },
        )
    }
}
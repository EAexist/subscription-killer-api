package com.matchalab.subscription_killer_api.ai.service.prompt.emailtemplateextraction

import com.fasterxml.jackson.databind.ObjectMapper
import com.matchalab.subscription_killer_api.ai.dto.EmailTemplateExtractionResult
import com.matchalab.subscription_killer_api.ai.service.ExtractEmailTemplatesResponse
import com.matchalab.subscription_killer_api.ai.service.ExtractEmailTemplatesResponseItem
import com.matchalab.subscription_killer_api.ai.service.MockChatResponseService
import com.matchalab.subscription_killer_api.datasets.EmailDatasetProvider
import com.matchalab.subscription_killer_api.emailtemplate.EmailTemplate
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.subscription.SubscriptionEventType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Profile("!ai")
@Service
@Primary
class MockEmailTemplateExtractionPromptService(
    private val emailTemplateExtractionPromptServiceImpl: EmailTemplateExtractionPromptServiceImpl,
    private val emailDatasetProvider: EmailDatasetProvider,
    private val mockChatResponseService: MockChatResponseService,
    private val objectMapper: ObjectMapper
) : EmailTemplateExtractionPromptService {

    override fun run(messagesWithSubscriptionEventType: List<Pair<GmailMessage, SubscriptionEventType>>): List<EmailTemplateExtractionResult> {

        val messages = messagesWithSubscriptionEventType.map { it.first }

        emailTemplateExtractionPromptServiceImpl.run(messagesWithSubscriptionEventType)

        val result: List<ExtractEmailTemplatesResponseItem> =
            messages.withIndex().map { (index, message) ->
                val template: EmailTemplate =
                    emailDatasetProvider.getTemplate(message.templateId!!)!!
                ExtractEmailTemplatesResponseItem(
                    m = index,
                    j = template.subjectAnchors,
                    p = template.snippetAnchors
                )
            }
        val extractResponse = ExtractEmailTemplatesResponse(result)

        val resultJson = convertResultToJson(extractResponse)

        mockChatResponseService.generateMockChatContext(
            taskName = "extract_email_templates",
            prompt = emailTemplateExtractionPromptServiceImpl.getPrompt(),
            params = emailTemplateExtractionPromptServiceImpl.getParams(
                messagesWithSubscriptionEventType
            ),
            outputFormattingString = "{\"result\":[]}",
            dataCount = messages.size,
            outputString = resultJson
        )

        val response = messages.map {
            EmailTemplateExtractionResult(
                it.id,
                emailDatasetProvider.getTemplate(it.templateId!!)!!
            )
        }

        return response
    }

    private fun <T> convertResultToJson(result: T): String {
        return objectMapper.writeValueAsString(result)
    }

}
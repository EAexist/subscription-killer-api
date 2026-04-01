package com.matchalab.subscription_killer_api.ai.service.prompt.emailtemplateextraction

import com.fasterxml.jackson.databind.ObjectMapper
import com.matchalab.subscription_killer_api.ai.dto.EmailTemplateExtractionResult
import com.matchalab.subscription_killer_api.ai.service.ExtractEmailTemplatesResponse
import com.matchalab.subscription_killer_api.ai.service.ExtractEmailTemplatesResponseItem
import com.matchalab.subscription_killer_api.ai.service.MockChatResponseService
import com.matchalab.subscription_killer_api.datasets.EmailDatasetProvider
import com.matchalab.subscription_killer_api.emailtemplate.EmailTemplate
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Primary
@Service
class MockEmailTemplateExtractionPromptService(
    private val emailTemplateExtractionPromptServiceImpl: EmailTemplateExtractionPromptServiceImpl,
    private val emailDatasetProvider: EmailDatasetProvider,
    private val mockChatResponseService: MockChatResponseService,
    private val objectMapper: ObjectMapper
) : EmailTemplateExtractionPromptService {

    override fun run(messages: List<GmailMessage>): List<EmailTemplateExtractionResult> {

        emailTemplateExtractionPromptServiceImpl.run(messages)

        val result: List<ExtractEmailTemplatesResponseItem> = messages.withIndex().map { (index, message) ->
            val template: EmailTemplate = emailDatasetProvider.getTemplate(message.id)!!
            ExtractEmailTemplatesResponseItem(
                m =  index,
                j = template.subjectAnchors,
                p = template.snippetAnchors
            )
        }
        val extractResponse = ExtractEmailTemplatesResponse(result)

        val resultJson = convertResultToJson(extractResponse)

        mockChatResponseService.generateMockChatContext(
            taskName = "extract_email_templates",
            prompt = emailTemplateExtractionPromptServiceImpl.getPrompt(),
            params = emailTemplateExtractionPromptServiceImpl.getParams(messages),
            outputFormattingString = "{\"result\":[]}",
            dataCount = messages.size,
            outputString = resultJson
        )

        val response = messages.map { EmailTemplateExtractionResult(it.id, emailDatasetProvider.getTemplate(it.id)?: EmailTemplate(listOf(it.subject), listOf(it.snippet)) )}

        return response
    }

    private fun <T> convertResultToJson(result: T): String {
        return objectMapper.writeValueAsString(result)
    }

}
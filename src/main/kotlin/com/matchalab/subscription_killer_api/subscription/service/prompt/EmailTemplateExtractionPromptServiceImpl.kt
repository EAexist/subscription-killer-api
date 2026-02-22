package com.matchalab.subscription_killer_api.subscription.service.prompt

import com.matchalab.subscription_killer_api.ai.dto.EmailTemplateExtractionPromptParams
import com.matchalab.subscription_killer_api.ai.dto.EmailTemplateExtractionResponse
import com.matchalab.subscription_killer_api.ai.dto.EmailTemplateExtractionResult
import com.matchalab.subscription_killer_api.ai.service.ChatClientService
import com.matchalab.subscription_killer_api.ai.service.config.PromptTemplateProperties
import com.matchalab.subscription_killer_api.ai.toPromptParamString
import com.matchalab.subscription_killer_api.subscription.EmailTemplate
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class EmailTemplateExtractionPromptServiceImpl(
    private val chatClientService: ChatClientService,
    private val promptTemplateProperties: PromptTemplateProperties,
) : EmailTemplateExtractionPromptService {


    override fun run(messages: List<GmailMessage>): EmailTemplateExtractionResponse {

        val params =
            EmailTemplateExtractionPromptParams(messages.withIndex().joinToString("\n") { (index, it) ->
                it.toPromptParamString(
                    index
                )
            })

        logger.debug { "[run] ✨  Calling chatClient for ${messages.size} messages" }

        val prompt: String = promptTemplateProperties.generalizeStringPattern.getContentAsString(Charsets.UTF_8).trimIndent()

        return chatClientService.extractEmailTemplates(
            prompt,
            mapOf("emails" to params.emails),
            messages.size
        ).let { response ->
            EmailTemplateExtractionResponse(
                result = response.result.map {
                    EmailTemplateExtractionResult(
                        messages[it["m"]!!.toInt()].id,
                        EmailTemplate(
                            it["j"].toString(),
                            it["p"].toString()
                        )
                    )
                },
            )
        }
    }
}
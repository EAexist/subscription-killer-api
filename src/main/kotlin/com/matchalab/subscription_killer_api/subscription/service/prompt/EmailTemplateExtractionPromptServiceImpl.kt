package com.matchalab.subscription_killer_api.subscription.service.prompt

import com.matchalab.subscription_killer_api.ai.dto.EmailTemplateExtractionPromptParams
import com.matchalab.subscription_killer_api.ai.dto.EmailTemplateExtractionResponse
import com.matchalab.subscription_killer_api.ai.dto.EmailTemplateExtractionResult
import com.matchalab.subscription_killer_api.ai.service.ChatClientService
import com.matchalab.subscription_killer_api.ai.service.call
import com.matchalab.subscription_killer_api.ai.service.config.PromptTemplateProperties
import com.matchalab.subscription_killer_api.ai.toPromptParamString
import com.matchalab.subscription_killer_api.subscription.EmailTemplate
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.utils.observe
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.observation.ObservationRegistry
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Profile("ai")
@Service
class EmailTemplateExtractionPromptServiceImpl(
    private val chatClientService: ChatClientService,
    private val promptTemplateProperties: PromptTemplateProperties,
    private val observationRegistry: ObservationRegistry
) : EmailTemplateExtractionPromptService {

    private class ResponseWrapper(
        val result: List<Map<String, String>>
    )

    override fun run(messages: List<GmailMessage>): EmailTemplateExtractionResponse {

        val promptParams =
            EmailTemplateExtractionPromptParams(messages.withIndex().joinToString("\n") { (index, it) ->
                it.toPromptParamString(
                    index
                )
            })

        logger.debug { "[run] ✨  Calling chatClient for ${messages.size} messages" }

        return observationRegistry.observe(
            "prompt_service email_template_extraction",
            "task" to "email_template_extraction"
        ) {
            chatClientService.call<ResponseWrapper>(
                promptTemplateProperties.generalizeStringPattern,
                mapOf("emails" to promptParams.emails)
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
}
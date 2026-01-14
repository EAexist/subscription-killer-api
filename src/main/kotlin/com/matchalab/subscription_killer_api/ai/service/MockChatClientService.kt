package com.matchalab.subscription_killer_api.ai.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.matchalab.subscription_killer_api.ai.dto.EmailCategorizationResponse
import com.matchalab.subscription_killer_api.ai.dto.EmailTemplateExtractionResponse
import com.matchalab.subscription_killer_api.ai.dto.EmailTemplateExtractionResult
import com.matchalab.subscription_killer_api.subscription.EmailTemplate
import com.matchalab.subscription_killer_api.subscription.service.EmailCategorizationResponseFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
@Profile("!ai")
class MockChatClientService(
) : ChatClientService {

    private val objectMapper = jacksonObjectMapper()

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> call(
        promptTemplateStream: Resource,
        params: Map<String, Any>,
        responseType: Class<T>
    ): T {
        val promptTemplate: String = promptTemplateStream.getContentAsString(Charsets.UTF_8).trimIndent()

        if ("""
            Output format (exact keys):
            \{
               "M": ["ID1", "ID2"],
               "A": [],
               "S": ["ID3"],
               "C": ["ID4"]
            \}
        """.trimIndent() in promptTemplate
        ) {
            val emailCategorizationResponse: EmailCategorizationResponse =
                EmailCategorizationResponseFactory.createSample()
            return mapOf(
                "M" to emailCategorizationResponse.monthlyMsgIds,
                "A" to emailCategorizationResponse.annualMsgIds,
                "S" to emailCategorizationResponse.subsStartMsgIds,
                "C" to emailCategorizationResponse.subsCancelMsgIds,
            ) as T
        }

        if ("""
            Output format (exact keys):
            \{
              "result": [
                \{
                  "messageIds": ["123","456"],
                  "template": \{
                    "subjectRegex": "regex-for-subject",
                    "snippetRegex": "regex-for-snippet"
                  \}
                \}
              ]
            \}
        """.trimIndent() in promptTemplate
        ) {
            val result = (params["emails"] as String).lines().map { line ->
                val parts = line.split("|")
                EmailTemplateExtractionResult(
                    listOf(parts[0]),
                    EmailTemplate(
                        parts[1],
                        parts[2]
                    )
                )
            }
            return EmailTemplateExtractionResponse(
                result
            ) as T
        }
        return responseType.getDeclaredConstructor().newInstance()
    }
}
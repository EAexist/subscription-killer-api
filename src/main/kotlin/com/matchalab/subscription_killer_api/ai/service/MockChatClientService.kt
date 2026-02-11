package com.matchalab.subscription_killer_api.ai.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.matchalab.subscription_killer_api.ai.dto.EmailTemplateExtractionResponse
import com.matchalab.subscription_killer_api.ai.dto.EmailTemplateExtractionResult
import com.matchalab.subscription_killer_api.subscription.EmailTemplate
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import java.util.regex.Pattern

private val logger = KotlinLogging.logger {}

@Service
@Profile("!ai")
@Deprecated("deprecated")
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

        if ("""You are an email classifier.""".trimIndent() in promptTemplate
        ) {
            return mapOf(
                "M" to listOf(0),
                "A" to listOf(1),
                "S" to listOf(2),
                "C" to listOf(3),
            ) as T
        }

        if ("""You are a literal string extractor.""".trimIndent() in promptTemplate
        ) {
            val result = (params["emails"] as String).lines().map { line ->
                val parts = line.split("|")
                EmailTemplateExtractionResult(
                    parts[0],
                    EmailTemplate(
                        Pattern.quote(parts[1]),
                        Pattern.quote(parts[2])
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
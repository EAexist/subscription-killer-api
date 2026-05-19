package com.matchalab.sublog_api.ai.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
@Profile("!ai")
class MockChatClientService(
) : ChatClientService {

    override fun categorizeEmails(
        prompt: String,
        params: Map<String, String>,
        dataCount: Int?
    ): Map<String, List<Int>> {
        return mapOf()
    }

    override fun extractEmailTemplates(
        prompt: String,
        params: Map<String, String>,
        dataCount: Int?
    ): ExtractEmailTemplatesResponse {

        return ExtractEmailTemplatesResponse()
    }
}
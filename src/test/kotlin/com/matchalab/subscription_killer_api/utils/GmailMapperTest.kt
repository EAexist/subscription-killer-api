package com.matchalab.subscription_killer_api.utils

import com.google.api.services.gmail.model.Message
import com.matchalab.subscription_killer_api.config.TestDataFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Import
import kotlin.test.Test

private val logger = KotlinLogging.logger {}

@Import(TestDataFactory::class)
class GmailMapperTest() {
    private val factory = TestDataFactory()
    private val sampleMessages: List<Message> = factory.loadSampleRawMessages()
    private val maxSnippetSize = 100

    @Test
    fun `test character savings and cleaning optimization`() {

        sampleMessages.forEach { message ->
            val gmailMessage = message.toGmailMessage(maxSnippetSize)
            val headerSubject =
                message.payload?.headers?.find { it.name.equals("Subject", ignoreCase = true) }?.value ?: ""

            logger.info { "Subject: ${gmailMessage.subject}" }
            logger.info { "\tSnippet(Before): ${message.snippet}" }
            logger.info { "\tSnippet(After): ${gmailMessage.snippet}" }
            logger.info { "\tSubject: ${headerSubject.length} -> ${gmailMessage.subject.length}" }
            logger.info { "\tSnippet: ${message.snippet.length} -> ${gmailMessage.snippet.length}" }
        }
    }
}
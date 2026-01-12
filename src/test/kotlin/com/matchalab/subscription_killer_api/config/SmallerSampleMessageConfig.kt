package com.matchalab.subscription_killer_api.config

import com.google.api.services.gmail.model.Message
import com.matchalab.subscription_killer_api.utils.toGmailMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

private val logger = KotlinLogging.logger {}

@TestConfiguration
class SmallerSampleMessageConfig {

    private val sampleEmails = listOf("info@account.netflix.com", "do-not-reply@watcha.com")

    @Bean
    @Primary
    fun limitedSampleMessages(originalMessages: List<Message>): List<Message> {
        return originalMessages.filter { it.toGmailMessage()?.senderEmail in sampleEmails }
    }
}
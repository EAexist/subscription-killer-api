package com.matchalab.subscription_killer_api.config

import com.matchalab.subscription_killer_api.subscription.GmailMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

private val logger = KotlinLogging.logger {}

@TestConfiguration
class SmallerSampleMessageConfig {

    private val sampleEmails = listOf("do-not-reply@watcha.com", "info@account.netflix.com")

    @Bean
    @Primary
    fun limitedSampleMessages(originalMessages: List<GmailMessage>): List<GmailMessage> {
        logger.debug { "[limitedSampleMessages]" }
        return originalMessages.filter { it.senderEmail in sampleEmails }
    }
}
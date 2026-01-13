package com.matchalab.subscription_killer_api.config

import com.matchalab.subscription_killer_api.domain.LocaleType
import com.matchalab.subscription_killer_api.subscription.EmailDetectionRule
import com.matchalab.subscription_killer_api.subscription.EmailSource
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.subscription.ServiceProvider
import com.matchalab.subscription_killer_api.utils.readMessages
import com.matchalab.subscription_killer_api.utils.toGmailMessage
import org.springframework.core.io.ClassPathResource
import java.util.*

open class TestDataFactory(
) {

    fun createServiceProvider(
        displayName: String,
        emailSources: MutableList<EmailSource>?,
    ) =
        ServiceProvider(
            UUID.randomUUID(),
            displayName,
            "$displayName.com",
            "www.$displayName.com",
            mutableMapOf(LocaleType.EN.name to displayName),
            emailSources ?: mutableListOf<EmailSource>()
        )

    fun createEmailSource(
        targetAddress: String,
        eventRules: MutableList<EmailDetectionRule> = mutableListOf()
    ) =
        EmailSource(null, targetAddress, eventRules)

    fun loadSampleMessages(): List<GmailMessage> {
        val jsonPath = "static/messages/sample_messages_netflix_sketchfab.json"
        return readMessages(ClassPathResource(jsonPath).inputStream).map { it.toGmailMessage() }
    }
}
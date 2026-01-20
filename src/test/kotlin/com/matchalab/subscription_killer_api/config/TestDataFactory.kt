package com.matchalab.subscription_killer_api.config

import com.google.api.services.gmail.model.Message
import com.matchalab.subscription_killer_api.domain.LocaleType
import com.matchalab.subscription_killer_api.subscription.EmailDetectionRule
import com.matchalab.subscription_killer_api.subscription.EmailSource
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.subscription.ServiceProvider
import com.matchalab.subscription_killer_api.utils.readMessages
import com.matchalab.subscription_killer_api.utils.toGmailMessage
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
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
            "www.$displayName.com",
            mutableMapOf(LocaleType.EN.name to displayName),
            emailSources ?: mutableListOf<EmailSource>()
        )

    fun createEmailSource(
        targetAddress: String,
        eventRules: MutableList<EmailDetectionRule> = mutableListOf(),
        analyzedMessageIds: MutableSet<String> = mutableSetOf()
    ) =
        EmailSource(null, targetAddress, eventRules, analyzedMessageIds = analyzedMessageIds)

    fun loadSampleMessages(): List<GmailMessage> {
        val jsonPath = "static/messages/sample_messages_netflix_sketchfab.json"
        return readMessages(ClassPathResource(jsonPath).inputStream).map { it.toGmailMessage() }
    }

    fun loadSampleRawMessages(): List<Message> {
        val dir = "private/messages"
        val fallback = "static/messages/sample_messages_netflix_sketchfab.json"
        val resolver = PathMatchingResourcePatternResolver()
        val resources = resolver.getResources("classpath:$dir/*.json")
        var messages: List<Message>

        if (resources.isEmpty()) {
            val resource = ClassPathResource(fallback)
            messages = readMessages(resource.inputStream)
        } else {
            messages = resources.flatMap { resource ->
                resource.inputStream.use { inputStream ->
                    readMessages(inputStream)
                }
            }
        }

        return messages
    }
}
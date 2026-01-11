package com.matchalab.subscription_killer_api.config

import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.model.Message
import com.matchalab.subscription_killer_api.domain.LocaleType
import com.matchalab.subscription_killer_api.subscription.EmailDetectionRule
import com.matchalab.subscription_killer_api.subscription.EmailSource
import com.matchalab.subscription_killer_api.subscription.ServiceProvider
import com.matchalab.subscription_killer_api.subscription.SubscriptionEventType
import org.springframework.core.io.ClassPathResource
import java.io.InputStream
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

    fun loadSampleMessages(): List<Message> {
        val jsonPath = "static/messages/sample_messages_netflix_sketchfab.json"
        return readMessages(ClassPathResource(jsonPath).inputStream)
    }

    fun readMessages(inputStream: InputStream): List<Message> {
        val factory = GsonFactory.getDefaultInstance()
        val parser = factory.createJsonParser(inputStream)
        val messages = mutableListOf<Message>()
        parser.parseArray(messages, Message::class.java)
        return messages
    }
}
package com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter

import com.matchalab.subscription_killer_api.gmail.MessageFetchPlan
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.utils.readMessages
import com.matchalab.subscription_killer_api.utils.toGmailMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Profile("prod || gmail")
@Component
class MockGmailClientAdapter() : GmailClientAdapter {

    val jsonPath = "static/messages_netflix_sketchfab.json"
    val sampleMessages =
        readMessages(ClassPathResource(jsonPath).inputStream).mapNotNull { it.toGmailMessage() }.associateBy { it.id }

    override suspend fun listMessageIds(query: String): List<String> {
        return sampleMessages.values.map { it.id }
    }

    override suspend fun getMessages(messageIds: List<String>, plan: MessageFetchPlan): List<GmailMessage> {
        return messageIds.mapNotNull { sampleMessages[it] }
    }

    override suspend fun getFirstMessageId(addresses: List<String>): String? {
        return sampleMessages.values.first().id
    }
}

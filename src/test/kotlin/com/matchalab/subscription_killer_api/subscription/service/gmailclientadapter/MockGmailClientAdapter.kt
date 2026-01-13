package com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter

import com.google.api.services.gmail.model.Message
import com.matchalab.subscription_killer_api.config.SampleMessageConfig
import com.matchalab.subscription_killer_api.gmail.MessageFetchPlan
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.subscription.config.MailProperties
import com.matchalab.subscription_killer_api.utils.toGmailMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Profile("!google-auth || !gmail")
@Component
@Import(SampleMessageConfig::class)
class MockGmailClientAdapter(
    private val mailProperties: MailProperties,
    private val sampleMessages: List<Message>
) : GmailClientAdapter {

    val idToSampleMessages =
        sampleMessages.mapNotNull { it.toGmailMessage(mailProperties.maxSnippetSize) }.sortedBy { it.internalDate }
            .associateBy { it.id }

    override suspend fun listMessageIds(query: String): List<String> {
        return idToSampleMessages.values.map { it.id }
    }

    override suspend fun getMessages(messageIds: List<String>, plan: MessageFetchPlan): List<GmailMessage> {
        return messageIds.mapNotNull { idToSampleMessages[it] }
    }

    override suspend fun getFirstMessageId(addresses: List<String>): String? {
        return idToSampleMessages.values.first { it.senderEmail in addresses }.id
    }
}

package com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter

import com.matchalab.subscription_killer_api.datasets.EmailDatasetProvider
import com.matchalab.subscription_killer_api.gmail.MessageFetchPlan
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
@Profile("!gmail")
class MockGmailClientAdapter(
    private val emailDatasetProvider: EmailDatasetProvider
) : GmailClientAdapter {

    private val idToSampleMessages: Map<String, GmailMessage> =
        emailDatasetProvider.getSampleMessageSet().associateBy { it.id }

    override suspend fun listMessageIds(query: String): List<String> {
        return idToSampleMessages.values.map { it.id }
    }

    override suspend fun getMessages(
        messageIds: List<String>,
        plan: MessageFetchPlan
    ): List<GmailMessage> {
        return messageIds.mapNotNull { idToSampleMessages[it] }
    }

    override suspend fun getFirstMessageId(addresses: List<String>, q: String): String? {
        return idToSampleMessages.values.first { it.senderEmail in addresses }.id
    }

    override suspend fun getFirstMessageId(addresses: List<String>): String? {
        return idToSampleMessages.values.first { it.senderEmail in addresses }.id
    }
}

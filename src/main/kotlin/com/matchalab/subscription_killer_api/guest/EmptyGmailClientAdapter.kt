package com.matchalab.subscription_killer_api.guest

import com.matchalab.subscription_killer_api.gmail.MessageFetchPlan
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter.GmailClientAdapter
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class EmptyGmailClientAdapter(
) : GmailClientAdapter {
    override suspend fun listMessageIds(query: String): List<String> {
        return listOf()
    }

    override suspend fun getMessages(
        messageIds: List<String>,
        plan: MessageFetchPlan
    ): List<GmailMessage> {
        return listOf()
    }

    override suspend fun getFirstMessageId(addresses: List<String>, q: String): String? {
        return null
    }

    override suspend fun getFirstMessageId(addresses: List<String>): String? {
        return null
    }
}

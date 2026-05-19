package com.matchalab.sublog_api.subscription.service.gmailclientadapter

import com.matchalab.sublog_api.gmail.MessageFetchPlan
import com.matchalab.sublog_api.subscription.GmailMessage
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

interface GmailClientAdapter {

    suspend fun listMessageIds(
        query: String,
    ): List<String>

    suspend fun getMessages(messageIds: List<String>, plan: MessageFetchPlan): List<GmailMessage>

    suspend fun getFirstMessageId(addresses: List<String>, q: String): String?

    suspend fun getFirstMessageId(addresses: List<String>): String?
}

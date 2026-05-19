package com.matchalab.sublog_api.subscription.service.gmailclientadapter

import com.matchalab.sublog_api.gmail.MessageFetchPlan
import com.matchalab.sublog_api.subscription.GmailMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.observation.ObservationRegistry

private val logger = KotlinLogging.logger {}

class ObservingGmailClientAdapter(
    private val delegate: GmailClientAdapter,
    private val observationRegistry: ObservationRegistry
) : GmailClientAdapter {

    override suspend fun listMessageIds(query: String): List<String> {

//        return observationRegistry.observeSuspend(
//            "gmail_client_adapter list_message_ids",
//            "query" to query
//        ) {
        // Optional: attach high-cardinality data like query as a KeyValue
        return delegate.listMessageIds(query)
//        }
    }

    override suspend fun getMessages(
        messageIds: List<String>,
        plan: MessageFetchPlan
    ): List<GmailMessage> {

//        return observationRegistry.observeSuspend(
//            "gmail_client_adapter get_messages",
//            "fields" to plan.fields
//        ) {
        return delegate.getMessages(messageIds, plan)
//        }
    }

    override suspend fun getFirstMessageId(addresses: List<String>, q: String): String? {

//        return observationRegistry.observeSuspend(
//            "gmail_client_adapter get_first_messageId",
//            "addresses" to (addresses.firstOrNull() ?: "none")
//        ) {
        return delegate.getFirstMessageId(addresses, q)
//        }
    }

    override suspend fun getFirstMessageId(addresses: List<String>): String? {

//        return observationRegistry.observeSuspend(
//            "gmail_client_adapter get_first_messageId",
//            "addresses" to (addresses.firstOrNull() ?: "none")
//        ) {
        return delegate.getFirstMessageId(addresses)
//        }
    }
}
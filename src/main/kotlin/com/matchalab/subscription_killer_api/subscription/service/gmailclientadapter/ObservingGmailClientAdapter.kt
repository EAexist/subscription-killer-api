package com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter

import com.matchalab.subscription_killer_api.gmail.MessageFetchPlan
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.utils.observeSuspend
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.observation.ObservationRegistry
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class ObservingGmailClientAdapter(
    private val delegate: GmailClientAdapter,
    private val observationRegistry: ObservationRegistry
) : GmailClientAdapter {

    override suspend fun listMessageIds(query: String): List<String> {
        val parent = observationRegistry.currentObservation

        return observationRegistry.observeSuspend(
            "gmail.listMessageIds",
            parent,
            "gmail.query" to query
        ) {
            // Optional: attach high-cardinality data like query as a KeyValue
            delegate.listMessageIds(query)
        }
    }

    override suspend fun getMessages(messageIds: List<String>, plan: MessageFetchPlan): List<GmailMessage> {
        val parent = observationRegistry.currentObservation

        return observationRegistry.observeSuspend(
            "gmail.getMessages",
            parent,
            "gmail.fields" to plan.fields
        ) {
            delegate.getMessages(messageIds, plan)
        }
    }

    override suspend fun getFirstMessageId(addresses: List<String>): String? {
        val parent = observationRegistry.currentObservation

        return observationRegistry.observeSuspend(
            "gmail.getFirstMessageId",
            parent,
            "gmail.addresses" to (addresses.firstOrNull() ?: "none")
        ) {
            delegate.getFirstMessageId(addresses)
        }
    }
}
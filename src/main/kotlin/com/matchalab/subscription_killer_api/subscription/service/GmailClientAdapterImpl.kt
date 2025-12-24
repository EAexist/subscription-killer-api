package com.matchalab.subscription_killer_api.subscription.service

import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailRequest
import com.google.api.services.gmail.model.ListMessagesResponse
import com.google.api.services.gmail.model.Message
import com.matchalab.subscription_killer_api.gmail.MessageFetchPlan
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.utils.toGmailMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import java.io.IOException
import java.util.*


private val logger = KotlinLogging.logger {}

class GmailClientAdapterImpl(private val gmailClient: Gmail) : GmailClientAdapter {

    val userId = "me"

    override suspend fun listMessageIds(query: String): List<String> = coroutineScope {

        logger.debug { "[listMessageIds] query: $query" }

        val messages = mutableListOf<Message>()
        var pageToken: String? = null

        do {
            val listResponse: ListMessagesResponse =
                try {
                    gmailClient
                        .users()
                        .messages()
                        .list(userId)
                        .setQ(query)
                        .setPageToken(pageToken)
                        .setMaxResults(1L)
                        .execute()
                } catch (e: Exception) {
                    println("Error listing messages: ${e.message}")
                    break
                }

            val pageMessages = listResponse.messages ?: emptyList()
            pageToken = listResponse.nextPageToken

            if (pageMessages.isEmpty()) {
                break
            }

            pageMessages.filterNotNull().forEach { messages.add(it) }
        } while (pageToken != null)


        logger.debug { "[listMessageIds] fetched ${messages.size} messages" }
        messages.map { it.id }
    }

    override suspend fun getMessages(messageIds: List<String>, plan: MessageFetchPlan): List<GmailMessage> {

        logger.debug { "fetching ${messageIds.size} messages" }

        val results = Collections.synchronizedList(mutableListOf<Message>())

        val callback: JsonBatchCallback<Message?> = object : JsonBatchCallback<Message?>() {
            override fun onSuccess(message: Message?, responseHeaders: HttpHeaders) {
                message?.let { results.add(it) }
            }

            override fun onFailure(e: GoogleJsonError, responseHeaders: HttpHeaders?) {
                logger.error { "fetch failed ${e.message}" }
            }
        }

        executeGmailBatch(messageIds, callback, {
            gmailClient
                .users()
                .messages()
                .get("me", it)
                .setFormat(plan.format)
                .setFields(plan.fields)
                .setMetadataHeaders(plan.metadataHeaders)
        })

        return results.mapNotNull { it.toGmailMessage() }
    }

    @Throws(IOException::class)
    fun <T> executeGmailBatch(
        ids: List<String?>,
        callback: JsonBatchCallback<T>,
        requestBuilder: (String) -> GmailRequest<T>
    ) {
        if (ids.isEmpty()) return

        val batch = gmailClient.batch()

        // Process in chunks if necessary (Gmail Batch limit is 100)
        ids.filterNotNull().take(100).forEach { id ->
            val request = requestBuilder(id)
            request.queue(batch, callback)
        }

        try {
            batch.execute()
        } catch (e: Exception) {
        }
    }
}

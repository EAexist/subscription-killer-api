package com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

private val logger = KotlinLogging.logger {}

class GmailClientAdapterImpl(private val gmailClient: Gmail) : GmailClientAdapter {

    val userId = "me"

    override suspend fun listMessageIds(query: String): List<String> = withContext(Dispatchers.IO) {

        logger.debug { "\uD83D\uDE80 query: $query" }

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

    override suspend fun getMessages(messageIds: List<String>, plan: MessageFetchPlan): List<GmailMessage> =
        withContext(Dispatchers.IO) {

            logger.debug { "\uD83D\uDE80 fetching ${messageIds.size} messages" }

            val results = mutableListOf<Message>()

            val callback = object : JsonBatchCallback<Message>() {
                override fun onSuccess(message: Message?, responseHeaders: HttpHeaders) {
                    message?.let { results.add(it) }
                }

                override fun onFailure(e: GoogleJsonError, responseHeaders: HttpHeaders?) {
                    logger.error { "fetch failed ${e.message}" }
                }
            }

            messageIds.chunked(100).forEach { chunk ->
                executeGmailBatch(chunk, callback) { id ->
                    gmailClient
                        .users()
                        .messages()
                        .get("me", id)
                        .setFormat(plan.format)
                        .setFields(plan.fields)
                        .setMetadataHeaders(plan.metadataHeaders)
                }
            }
            results.mapNotNull { it.toGmailMessage() }
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

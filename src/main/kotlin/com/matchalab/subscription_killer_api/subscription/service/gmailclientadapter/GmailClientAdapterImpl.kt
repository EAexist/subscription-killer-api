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
import io.micrometer.observation.annotation.Observed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.*

private val logger = KotlinLogging.logger {}

open class GmailClientAdapterImpl(private val gmailClient: Gmail) : GmailClientAdapter {

    private val userId = "me"
    private val mutex = Mutex() // Forces sequential execution

    @Observed
    open override suspend fun listMessageIds(query: String): List<String> = withContext(Dispatchers.IO) {

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

    @Observed
    open override suspend fun getMessages(messageIds: List<String>, plan: MessageFetchPlan): List<GmailMessage> =
        withContext(Dispatchers.IO) {
            val results = Collections.synchronizedList(mutableListOf<Message>())

            messageIds.chunked(25).forEach { chunk ->
                // 1. Ensure only one batch processes at a time
                mutex.withLock {
                    retryWithBackoff {
                        executeGmailBatch(chunk, object : JsonBatchCallback<Message>() {
                            override fun onSuccess(m: Message?, h: HttpHeaders) {
                                m?.let { results.add(it) }
                            }

                            override fun onFailure(e: GoogleJsonError, h: HttpHeaders?) {
                                if (e.code == 429) throw RateLimitException(e.message)
                                else logger.error { "Permanent failure: ${e.message}" }
                            }
                        }) { id ->
                            gmailClient.users().messages().get("me", id)
                                .setFormat(plan.format)
                                .setFields(plan.fields)
                        }
                    }
                    delay(100)
                }
            }
            results.mapNotNull { it.toGmailMessage() }
        }

    suspend fun <T> retryWithBackoff(
        maxRetries: Int = 5,
        initialDelay: Long = 1000,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: RateLimitException) {
                if (attempt == maxRetries - 1) throw e
                delay(currentDelay)
                currentDelay *= 2
            }
        }
        return block()
    }

    class RateLimitException(message: String) : Exception(message)


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
//    open override suspend fun getMessages(messageIds: List<String>, plan: MessageFetchPlan): List<GmailMessage> =
//        withContext(Dispatchers.IO) {
//
//            logger.debug { "\uD83D\uDE80 fetching ${messageIds.size} messages" }
//
//            val results = mutableListOf<Message>()
//
//            val callback = object : JsonBatchCallback<Message>() {
//                override fun onSuccess(message: Message?, responseHeaders: HttpHeaders) {
//                    message?.let { results.add(it) }
//                }
//
//                override fun onFailure(e: GoogleJsonError, responseHeaders: HttpHeaders?) {
//                    logger.error { "fetch failed ${e.message}" }
//                }
//            }
//
//            messageIds.chunked(100).forEach { chunk ->
//                executeGmailBatch(chunk, callback) { id ->
//                    gmailClient
//                        .users()
//                        .messages()
//                        .get("me", id)
//                        .setFormat(plan.format)
//                        .setFields(plan.fields)
//                        .setMetadataHeaders(plan.metadataHeaders)
//                }
//            }
//            results.mapNotNull { it.toGmailMessage() }
//        }
}

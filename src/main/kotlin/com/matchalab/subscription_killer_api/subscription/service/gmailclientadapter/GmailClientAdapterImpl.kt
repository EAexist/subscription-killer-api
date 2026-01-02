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
import com.matchalab.subscription_killer_api.utils.observeSuspend
import com.matchalab.subscription_killer_api.utils.toGmailMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.observation.ObservationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException

private val logger = KotlinLogging.logger {}

open class GmailClientAdapterImpl(
    private val gmailClient: Gmail,
    private val observationRegistry: ObservationRegistry
) : GmailClientAdapter {

    private val userId = "me"
    private val semaphore = Semaphore(5)
    private val getRequestChunkSize = 100

    open override suspend fun listMessageIds(query: String): List<String> {
        val parent = observationRegistry.currentObservation

        return observationRegistry.observeSuspend(
            "gmail.listMessageIds",
            parent,
            "gmail.query" to query
        ) {

            logger.debug { "\uD83D\uDE80 query: $query" }

            val messageIds = mutableListOf<String>()
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
                            .setMaxResults(500L)
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

                pageMessages.forEach { message -> message?.let { messageIds.add(it.id) } }

            } while (pageToken != null)


            logger.debug { "[listMessageIds] fetched ${messageIds.size} messages" }
            messageIds
        }
    }

    override suspend fun getMessages(messageIds: List<String>, plan: MessageFetchPlan): List<GmailMessage> {

        val parent = observationRegistry.currentObservation

        return observationRegistry.observeSuspend<List<GmailMessage>>(
            "gmail.getMessages",
            parent,
            "gmail.fields" to plan.fields
        ) {
            coroutineScope {
                messageIds.chunked(getRequestChunkSize)
                    .map { chunk ->
                        async {
                            semaphore.withPermit {
                                retryWithBackoff {
                                    val chunkResults = mutableListOf<Message>()
                                    executeGmailBatch(chunk, object : JsonBatchCallback<Message>() {
                                        override fun onSuccess(m: Message?, h: HttpHeaders) {
                                            m?.let { chunkResults.add(it) }
                                        }

                                        override fun onFailure(e: GoogleJsonError, h: HttpHeaders?) {
                                            if (e.code == 429) throw RateLimitException(e.message)
                                        }
                                    }) { id ->
                                        gmailClient.users().messages().get(userId, id)
                                            .setFormat(plan.format)
                                            .setFields(plan.fields)
                                    }
                                    chunkResults
                                }
                            }
                        }
                    }.awaitAll()
                    .flatten()
                    .mapNotNull { it.toGmailMessage() }
            }
        }
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

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import java.util.Collections.synchronizedList

private val logger = KotlinLogging.logger {}

open class GmailClientAdapterImpl(
    private val gmailClient: Gmail,
    private val observationRegistry: ObservationRegistry
) : GmailClientAdapter {

    private val userId = "me"
    private val semaphore = Semaphore(1)
    private val getRequestChunkSize = 15

    open override suspend fun listMessageIds(query: String): List<String> {
        val parent = observationRegistry.currentObservation

        return observationRegistry.observeSuspend(
            "gmail.listMessageIds",
            parent,
            "gmail.query" to query
        ) {

            logger.debug { "\uD83D\uDE80 | [listMessageIds] query: $query" }

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

            logger.debug { "🔊  [getMessages] Start fetching ${messageIds.size} messages" }

            messageIds.chunked(getRequestChunkSize).flatMap { chunk ->
                semaphore.withPermit {
                    retryWithBackoff {
                        val chunkResults = synchronizedList(mutableListOf<Message>())
                        executeGmailBatch(chunk, object : JsonBatchCallback<Message>() {
                            override fun onSuccess(m: Message?, h: HttpHeaders) {
                                m?.let { chunkResults.add(it) }
                            }

                            override fun onFailure(e: GoogleJsonError, h: HttpHeaders?) {
                                logger.error { "❌\u0020Batch item failed: ${e.message} (Code: ${e.code})" }
//                                if (e.code == 429) throw RateLimitException(e.message)
                            }
                        }) { id ->
                            gmailClient.users().messages().get(userId, id)
                                .setFormat(plan.format)
                                .setFields(plan.fields)
                        }
                        if (chunkResults.isEmpty() && chunk.isNotEmpty()) {
                            logger.debug { "❌\u0020getMessages | chunk not empty, chunkResults is empty" }
                        }
                        chunkResults
                    }
                }
            }
                .mapNotNull { it.toGmailMessage() }
        }
    }

    override suspend fun getFirstMessageId(addresses: List<String>): String? {
        val fromPart = addresses.joinToString(separator = " OR ") {
            "from:\"$it\""
        }
        val query = fromPart
        var pageToken: String? = null
        var oldestId: String? = null

        do {
            val listResponse = try {
                gmailClient.users().messages().list(userId)
                    .setQ(query)
                    .setPageToken(pageToken)
                    .setMaxResults(500L)
                    .setFields("nextPageToken,messages(id)") // Optimization: Only fetch IDs
                    .execute()
            } catch (e: Exception) {
                null
            } ?: break

            val messages = listResponse.messages ?: emptyList()
            if (messages.isNotEmpty()) {
                // The last item in the list on the current page
                oldestId = messages.last().id
            }

            pageToken = listResponse.nextPageToken
        } while (pageToken != null)

        return oldestId
    }


    suspend fun <T> retryWithBackoff(
        maxRetries: Int = 5,
        initialDelay: Long = 2000,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: RateLimitException) {
                if (attempt == maxRetries - 1) throw e
                val jitter = (currentDelay * 0.2).toLong()
                val delayWithJitter = currentDelay + (-jitter..jitter).random()
                delay(delayWithJitter)
                currentDelay *= 2
            }
        }
        throw IllegalStateException("Retry failed")
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

        ids.filterNotNull().forEach { id ->
            val request = requestBuilder(id)
            request.queue(batch, callback)
        }

        try {
            batch.execute()
        } catch (e: Exception) {
            logger.error { "❌\u0020[executeGmailBatch] ${e.message}" }
        }
    }
//    open override suspend fun getMessages(messageIds: List<String>, plan: MessageFetchPlan): List<GmailMessage> =
//        withContext(Dispatchers.IO) {
//
//            logger.debug { "\uD83D\uDE80 | fetching ${messageIds.size} messages" }
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

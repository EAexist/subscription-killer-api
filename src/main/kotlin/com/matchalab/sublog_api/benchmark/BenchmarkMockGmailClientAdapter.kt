package com.matchalab.sublog_api.benchmark

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.matchalab.sublog_api.gmail.MessageFetchPlan
import com.matchalab.sublog_api.subscription.GmailMessage
import com.matchalab.sublog_api.subscription.service.gmailclientadapter.GmailClientAdapter
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration

private val logger = KotlinLogging.logger {}

internal data class BatchGetRequest(
    @field:JsonProperty("message_ids")
    val messageIds: List<String>
)

internal data class FirstMessageIdRequest(
    @field:JsonProperty("addresses")
    val addresses: List<String>
)

internal data class BatchGetResponse(
    @JsonProperty("messages")
    val messages: List<GmailMessage>
)


@Component
@Profile("benchmark")
@EnableConfigurationProperties(BenchmarkProperties::class)
class BenchmarkMockGmailClientAdapter(
    private val benchmarkProperties: BenchmarkProperties,
    private val objectMapper: ObjectMapper,
) : GmailClientAdapter {

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .version(HttpClient.Version.HTTP_1_1)
        .build()

    override suspend fun listMessageIds(query: String): List<String> {

        val uri = UriComponentsBuilder.fromUriString(benchmarkProperties.baseUrl)
            .path("/messages")
            .queryParam("q", query)
            .build()
            .toUri()

//        logger.debug { "Connecting to: $uri" }

        val request = createGetRequest(uri, mapOf("X-Mock-User" to "current-user-context"))

        val response = withContext(Dispatchers.IO) {
            httpClient.send(request, BodyHandlers.ofString())
        }
        return objectMapper.readValue(response.body(), String::class.java).split(",")
            .filter { it.isNotBlank() }
    }

    override suspend fun getMessages(
        messageIds: List<String>,
        plan: MessageFetchPlan
    ): List<GmailMessage> {
        val uri = URI("${benchmarkProperties.baseUrl}/messages/batch-get")

        val requestBody = objectMapper.writeValueAsString(BatchGetRequest(messageIds))

//        logger.debug { "getMessages:\nrequestBody=${requestBody}" }

        val request =
            createPostRequest(uri, requestBody, mapOf("Content-Type" to "application/json"))

        val response = withContext(Dispatchers.IO) {
            httpClient.send(request, BodyHandlers.ofString())
        }

        val messages: List<GmailMessage> =
            objectMapper.readValue(response.body(), BatchGetResponse::class.java).messages

        return messages
    }

    override suspend fun getFirstMessageId(addresses: List<String>): String? {
        val uri = URI("${benchmarkProperties.baseUrl}/messages/first")

        val requestBody = objectMapper.writeValueAsString(FirstMessageIdRequest(addresses))

        val request =
            createPostRequest(uri, requestBody, mapOf("Content-Type" to "application/json"))

        val response = withContext(Dispatchers.IO) {
            httpClient.send(request, BodyHandlers.ofString())
        }
        return objectMapper.readValue(response.body(), String::class.java)
    }

    override suspend fun getFirstMessageId(addresses: List<String>, q: String): String? {
        return getFirstMessageId(addresses)
    }

    private fun createGetRequest(uri: URI, headers: Map<String, String> = emptyMap()): HttpRequest {
        val builder = HttpRequest.newBuilder().uri(uri).GET()
        headers.forEach { (key, value) -> builder.header(key, value) }
        return builder.build()
    }

    private fun createPostRequest(
        uri: URI,
        body: String,
        headers: Map<String, String> = emptyMap()
    ): HttpRequest {
        val builder = HttpRequest.newBuilder()
            .uri(uri)
            .POST(HttpRequest.BodyPublishers.ofString(body))
        headers.forEach { (key, value) -> builder.header(key, value) }
        return builder.build()
    }
}
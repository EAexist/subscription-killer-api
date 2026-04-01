package com.matchalab.subscription_killer_api.benchmark

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.matchalab.subscription_killer_api.gmail.MessageFetchPlan
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BenchmarkMockGmailClientAdapterTest {

    private lateinit var benchmarkMockGmailClientAdapter: BenchmarkMockGmailClientAdapter
    private lateinit var mockHttpClient: HttpClient
    private lateinit var mockHttpResponse: HttpResponse<String>
    private val wireMockBaseUrl = "http://localhost:8080"

    @BeforeEach
    fun setUp() {
        // Set main dispatcher for testing
        @OptIn(ExperimentalCoroutinesApi::class)
        Dispatchers.setMain(Dispatchers.Unconfined)

        // Mock the HttpClient to avoid actual HTTP calls
        mockHttpClient = mockk()
        mockHttpResponse = mockk()

        // Create the adapter with BenchmarkProperties
        val benchmarkProperties = BenchmarkProperties(wireMockBaseUrl)
        val objectMapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule()).disable(
            SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)

        // Mock HttpClient.newBuilder() to return our mock
        mockkStatic(HttpClient::class)
        every { HttpClient.newBuilder() } returns mockk {
            every { connectTimeout(any()) } returns this
            every { version(any()).build() } returns mockHttpClient
        }

        benchmarkMockGmailClientAdapter = BenchmarkMockGmailClientAdapter(benchmarkProperties, objectMapper)
    }

    @AfterEach
    fun tearDown() {
        @OptIn(ExperimentalCoroutinesApi::class)
        Dispatchers.resetMain()
        unmockkStatic(HttpClient::class)
    }

    @Test
    fun `listMessageIds should make GET request to messages endpoint with query parameter`() = runBlocking {
        // Given
        val query = "from:test@example.com"
        val expectedUri = "$wireMockBaseUrl/messages?q=$query"
        val responseBody = "\"msg1,msg2,msg3\""
        val expectedList = listOf("msg1", "msg2", "msg3")

        every {
            mockHttpClient.send(
                any<HttpRequest>(),
                any<HttpResponse.BodyHandler<String>>()
            )
        } returns mockHttpResponse
        every { mockHttpResponse.body() } returns responseBody

        // When
        val result = benchmarkMockGmailClientAdapter.listMessageIds(query)

        // Then
        verify {
            mockHttpClient.send(
                match<HttpRequest> { request ->
                    request.uri().toString() == expectedUri &&
                            request.method() == "GET" &&
                            request.headers().firstValue("X-Mock-User").orElse("") == "current-user-context"
                },
                any<HttpResponse.BodyHandler<String>>()
            )
        }
        assertEquals(expectedList, result)
    }

    @Test
    fun `getMessages should make POST request to batch-get endpoint`() = runBlocking {
        // Given
        val messageIds = listOf("msg1", "msg2")
        val plan = mockk<MessageFetchPlan>()
        val expectedUri = "$wireMockBaseUrl/messages/batch-get"
        val responseBody = """
        {
            "messages": [
                {
                    "id": "msg1",
                    "internalDate": "1710410000000",
                    "senderName": "Lucifer Morningstar",
                    "senderEmail": "test@example.com",
                    "subject": "Hello from Gemini",
                    "snippet": "This is a test snippet"
                }
            ]
        }
    """.trimIndent()

        val expectedMessages = listOf(GmailMessage(
            id = "msg1",
            internalDate = Instant.ofEpochMilli(1710410000000L),
            senderName = "Lucifer Morningstar",
            senderEmail = "test@example.com",
            subject = "Hello from Gemini",
            snippet = "This is a test snippet"
        ))

        every {
            mockHttpClient.send(
                any<HttpRequest>(),
                any<HttpResponse.BodyHandler<String>>()
            )
        } returns mockHttpResponse
        every { mockHttpResponse.body() } returns responseBody

        // When
        val result = benchmarkMockGmailClientAdapter.getMessages(messageIds, plan)

        // Then
        verify {
            mockHttpClient.send(
                match<HttpRequest> { request ->
                    request.uri().toString() == expectedUri &&
                            request.method() == "POST" &&
                            request.headers().firstValue("Content-Type").orElse("") == "application/json"
                },
                any<HttpResponse.BodyHandler<String>>()
            )
        }
        assertEquals(expectedMessages, result)
    }

    @Test
    fun `getFirstMessageId should make POST request to first endpoint`() = runBlocking {
        // Given
        val addresses = listOf("email1@example.com", "email2@example.com")
        val expectedUri = "$wireMockBaseUrl/messages/first"
        val responseBody = "\"mock-message-id\""
        val expectedResponse = "mock-message-id"

        every {
            mockHttpClient.send(
                any<HttpRequest>(),
                any<HttpResponse.BodyHandler<String>>()
            )
        } returns mockHttpResponse
        every { mockHttpResponse.body() } returns responseBody

        // When
        val result = benchmarkMockGmailClientAdapter.getFirstMessageId(addresses)

        // Then
        verify {
            mockHttpClient.send(
                match<HttpRequest> { request ->
                    request.uri().toString() == expectedUri &&
                            request.method() == "POST" &&
                            request.headers().firstValue("Content-Type").orElse("") == "application/json"
                },
                any<HttpResponse.BodyHandler<String>>()
            )
        }
        assertEquals(expectedResponse, result)
    }

    @Test
    fun `listMessageIds should handle HTTP exceptions gracefully`() = runBlocking {
        // Given
        val query = "from:test@example.com"
        val exception = RuntimeException("Network error")

        every { mockHttpClient.send(any<HttpRequest>(), any<HttpResponse.BodyHandler<String>>()) } throws exception

        // When & Then
        assertThrows<RuntimeException> {
            benchmarkMockGmailClientAdapter.listMessageIds(query)
        }
    }
}

package com.matchalab.subscription_killer_api.ai.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.matchalab.subscription_killer_api.datasets.EmailDatasetProvider
import com.matchalab.subscription_killer_api.datasets.EmailSample
import com.matchalab.subscription_killer_api.subscription.SubscriptionEventType
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MockChatClientServiceTest {

    private lateinit var service: MockChatClientService
    private lateinit var datasetProvider: EmailDatasetProvider
    private lateinit var emailSamples: List<EmailSample>
    private lateinit var objectMapper: ObjectMapper
    private lateinit var mockChatResponseService: MockChatResponseService

    @BeforeEach
    fun setUp() {
        objectMapper = ObjectMapper().registerKotlinModule()
        datasetProvider = EmailDatasetProvider(objectMapper)
        datasetProvider.loadEmailSamples()

        // Mock the new service
        mockChatResponseService = mockk<MockChatResponseService>()

        // Mock the generateMockChatContext to do nothing
        every {
            mockChatResponseService.generateMockChatContext(any(), any(), any(), any(), any(), any())
        } just Runs

        service = MockChatClientService(
            datasetProvider = datasetProvider,
            mockChatResponseService = mockChatResponseService,
            objectMapper = objectMapper
        )
        emailSamples = datasetProvider.getEmailSamples()
    }

    /**
     * Helper method to get expected indices for a specific subscription event type from email samples
     */
    private fun getExpectedIndices(eventType: SubscriptionEventType): List<Int> {
        return emailSamples
            .mapIndexed { index, data -> index to data.subscriptionEventType }
            .filter { (_, type) -> type == eventType }
            .map { (index, _) -> index }
    }

    @Test
    fun `categorizeEmails - given dataset with all subscription event types - should categorize correctly`() {
        // Given - using actual dataset converted to emailParams
        val emailParams = datasetProvider.createEmailParamsFromDataset()
        val params = mapOf("emails" to emailParams)

        // When
        val result = service.categorizeEmails("test prompt", params, null)

        // Then - verify all categories are correctly categorized
        assertEquals(getExpectedIndices(SubscriptionEventType.MONTHLY_PAYMENT), result["M"]) // Monthly payments
        assertEquals(getExpectedIndices(SubscriptionEventType.ANNUAL_PAYMENT), result["A"]) // Annual payments
        assertEquals(getExpectedIndices(SubscriptionEventType.SUBSCRIPTION_START), result["S"]) // Subscription starts
        assertEquals(
            getExpectedIndices(SubscriptionEventType.SUBSCRIPTION_CANCEL),
            result["C"]
        ) // Subscription cancellations
    }

    @Test
    fun `categorizeEmails - given unknown event types - should ignore them`() {
        // Given - using actual sample data loaded in setUp
        val emailParams =
            "0|Unknown Subject|Unknown content\n1|Your monthly subscription has been renewed|Netflix content"
        val params = mapOf("emails" to emailParams)

        // When
        val result = service.categorizeEmails("test prompt", params, null)

        // Then
        assertEquals(listOf(1), result["M"]) // Only known subject should be categorized
        assertEquals(emptyList<Int>(), result["A"]) // Annual payments
        assertEquals(emptyList<Int>(), result["S"]) // Subscription starts
        assertEquals(emptyList<Int>(), result["C"]) // Subscription cancellations
    }

    @Test
    fun `categorizeEmails - given subjects not in sample data - should ignore them`() {
        // Given - using actual sample data loaded in setUp
        val emailParams = "0|Unknown Subject|Unknown content\n1|Another Unknown|More unknown content"
        val params = mapOf("emails" to emailParams)

        // When
        val result = service.categorizeEmails("test prompt", params, 1)

        // Then
        assertEquals(emptyList<Int>(), result["M"]) // All categories should be empty
        assertEquals(emptyList<Int>(), result["A"]) // Annual payments
        assertEquals(emptyList<Int>(), result["S"]) // Subscription starts
        assertEquals(emptyList<Int>(), result["C"]) // Subscription cancellations
    }

    @Test
    fun `categorizeEmails - given empty email list - should return empty categories`() {
        // Given - using actual sample data loaded in setUp
        val emailParams = ""
        val params = mapOf("emails" to emailParams)

        // When
        val result = service.categorizeEmails("test prompt", params, 1)

        // Then
        assertEquals(emptyList<Int>(), result["M"]) // All categories should be empty
        assertEquals(emptyList<Int>(), result["A"])
        assertEquals(emptyList<Int>(), result["S"])
        assertEquals(emptyList<Int>(), result["C"])
    }

    @Test
    fun `extractEmailTemplates - given known and unknown subjects - should return correct templates`() {
        // Given - using actual dataset converted to emailParams plus unknown subjects
        val datasetEmailParams = datasetProvider.createEmailParamsFromDataset()
        val unknownEmailParams = "999|Unknown Subject|Unknown content\n1000|Another Unknown|More unknown content"
        val emailParams = "$datasetEmailParams\n$unknownEmailParams"
        val params = mapOf("emails" to emailParams)

        // When
        val result = service.extractEmailTemplates("test prompt", params, null)

        // Then
        assertEquals(emailSamples.size + 2, result.result.size)

        // Check known subjects have correct templates
        emailSamples.forEachIndexed { index, data ->
            val template = result.result[index]
            assertEquals(index.toString(), template["m"])
            assertEquals(data.subjectRegex, template["j"])
            assertEquals(data.snippetRegex, template["p"])
        }

        // Check unknown subjects have empty templates
        val unknownTemplate1 = result.result[emailSamples.size]
        assertEquals("999", unknownTemplate1["m"])
        assertEquals("", unknownTemplate1["j"])
        assertEquals("", unknownTemplate1["p"])

        val unknownTemplate2 = result.result[emailSamples.size + 1]
        assertEquals("1000", unknownTemplate2["m"])
        assertEquals("", unknownTemplate2["j"])
        assertEquals("", unknownTemplate2["p"])
    }

    @Test
    fun `extractEmailTemplates - given empty email list - should return empty result`() {
        // Given - using actual sample data loaded in setUp
        val emailParams = ""
        val params = mapOf("emails" to emailParams)

        // When
        val result = service.extractEmailTemplates("test prompt", params, null)

        // Then
        assertEquals(0, result.result.size)
    }
}

package com.matchalab.subscription_killer_api.emailtemplate.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.matchalab.subscription_killer_api.datasets.EmailDatasetProvider
import com.matchalab.subscription_killer_api.emailtemplate.EmailTemplate
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.utils.toGmailMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import java.time.Instant
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

class EmailTemplateMatchServiceTest {

    private lateinit var emailTemplateMatchService: EmailTemplateMatchService
    private lateinit var emailDatasetProvider: EmailDatasetProvider
    private lateinit var objectMapper: ObjectMapper
    private lateinit var resourcePatternResolver: PathMatchingResourcePatternResolver

    @BeforeEach
    fun setUp() {
        emailTemplateMatchService = EmailTemplateMatchService()
        objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        resourcePatternResolver = PathMatchingResourcePatternResolver()
        emailDatasetProvider = EmailDatasetProvider(objectMapper, resourcePatternResolver)
        emailDatasetProvider.init()
    }

    @Test
    fun `should match real email samples using EmailTemplateMatchService matchMessage method`() {
        // Given - Get email samples from EmailDatasetProvider
        val emailSamples = emailDatasetProvider.getEmailSamples()
        assertTrue(emailSamples.isNotEmpty(), "Email samples should not be empty for testing")
        
        // Test with a subset of samples to keep test reasonable
        val random = Random(456) // Fixed seed for reproducibility
        val selectedSamples = emailSamples.shuffled(random).take(10)
        
        // When & Then
        selectedSamples.forEach { emailSample ->
            val gmailMessage = emailSample.message
            val emailTemplate = emailSample.template

            val subjectMatches = emailTemplateMatchService.matches( gmailMessage.subject, emailTemplate.subjectAnchors )

            assertTrue(subjectMatches){
                """
                FAILED Match for EmailSample ID: ${gmailMessage.id}
                --------------------------------------------------
                Subject Match: $subjectMatches
                Actual Subject: '${gmailMessage.subject}'
                Expected Anchors: ${emailTemplate.subjectAnchors}
                --------------------------------------------------
                """.trimIndent()
            }

            val snippetMatches = emailTemplateMatchService.matches( gmailMessage.snippet, emailTemplate.snippetAnchors )
            assertTrue(snippetMatches){
                """
                FAILED Match for EmailSample ID: ${gmailMessage.id}
                --------------------------------------------------
                Snippet Match: $snippetMatches
                Actual Snippet: '${gmailMessage.snippet}'
                Expected Anchors: ${emailTemplate.snippetAnchors}
                --------------------------------------------------
                """.trimIndent()
            }

            val matches = emailTemplateMatchService.matchMessage(emailTemplate, gmailMessage)
            assertTrue(matches,
                "EmailSample ID ${gmailMessage.id} should match its template using EmailTemplateMatchService matchMessage method")
        }
    }

    @Test
    fun `should match message when both subject and snippet match using EmailTemplateMatchService`() {
        // Given
        val emailTemplate = EmailTemplate(
            subjectAnchors = listOf("Your order", "has shipped"),
            snippetAnchors = listOf("Track your package", "with tracking number")
        )
        val gmailMessage = GmailMessage(
            id = "test-123",
            internalDate = Instant.now(),
            senderName = "Test Store",
            senderEmail = "test@store.com",
            subject = "Your order 12345 has shipped",
            snippet = "Track your package 12345 with tracking number"
        )
        
        // When
        val matches = emailTemplateMatchService.matchMessage(emailTemplate, gmailMessage)
        
        // Then
        assertTrue(matches, "Should match when both subject and snippet match their anchor lists using EmailTemplateMatchService")
    }

    @Test
    fun `should test matches method directly`() {
        // Given
        val text = "Your order 12345 has shipped"
        val anchors = listOf("Your order", "has shipped")
        
        // When
        val matches = emailTemplateMatchService.matches(text, anchors)
        
        // Then
        assertTrue(matches, "Should match text against anchor list using matches method")
    }

    @Test
    fun `should test matches method with non-matching anchors`() {
        // Given
        val text = "Different content"
        val anchors = listOf("Your order", "has shipped")
        
        // When
        val matches = emailTemplateMatchService.matches(text, anchors)
        
        // Then
        assertFalse(matches, "Should not match text against anchor list using matches method")
    }

    @Test
    fun `should evict cache successfully`() {
        // Given - Add some patterns to cache by calling matches
        emailTemplateMatchService.matches("test", listOf("test"))
        
        // When
        emailTemplateMatchService.evictCache()
        
        // Then - Should not throw exception
        assertTrue(true, "Cache eviction should complete successfully")
    }

    @Test
    fun `should throw exception when anchors contain untrimmed strings`() {
        // Given
        val text = "Your order 12345 has shipped"
        val untrimmedAnchors = listOf("Your order ", " has shipped") // Second anchor has leading space
        
        // When & Then
        val exception = assertThrows(IllegalArgumentException::class.java) {
            emailTemplateMatchService.matches(text, untrimmedAnchors)
        }
        
        assertTrue(exception.message == "anchors contains untrimmed strings", 
            "Should throw exception with correct message for untrimmed anchors")
    }

}

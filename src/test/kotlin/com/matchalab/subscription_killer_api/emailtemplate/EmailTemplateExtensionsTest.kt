package com.matchalab.subscription_killer_api.emailtemplate

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.matchalab.subscription_killer_api.datasets.EmailDatasetProvider
import com.matchalab.subscription_killer_api.emailtemplate.service.EmailTemplateMatchService
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import java.time.Instant
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

class EmailTemplateExtensionsTest {

    private lateinit var emailDatasetProvider: EmailDatasetProvider
    private lateinit var objectMapper: ObjectMapper
    private lateinit var resourcePatternResolver: PathMatchingResourcePatternResolver

    @BeforeEach
    fun setUp() {
        objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        resourcePatternResolver = PathMatchingResourcePatternResolver()
        emailDatasetProvider =
            EmailDatasetProvider(objectMapper, resourcePatternResolver)
    }

    @Test
    fun `extractAnchors should convert template with placeholders to anchors`() {
        // Given
        val templates = emailDatasetProvider.loadTemplates().values.toList()
        assertTrue(templates.isNotEmpty(), "Templates should not be empty for testing")
        
        val random = Random(42) // Fixed seed for reproducibility
        val selectedTemplates = templates.shuffled(random).take(5)
        
        // When & Then - Test each randomly selected template
        selectedTemplates.forEach { template ->
            // Test subject
            testTemplateString(template.subject, "subject")
            testTemplateString(template.snippet, "snippet")
        }
    }
    
    private fun testTemplateString(templateString: String, fieldType: String) {
        logger.info { "Testing template $fieldType: '$templateString'" }
        
        val result = templateString.extractAnchors()
        logger.info { "Converted $fieldType anchors: $result" }
        
        // Then - Verify anchors are extracted properly
        if (templateString.contains("{{")) {
            // If template has placeholders, we should have multiple anchors
            assertTrue(result.isNotEmpty(), "$fieldType should have at least one anchor")
        } else {
            // If no placeholders, we should have one anchor with the full text
            assertEquals(1, result.size, "$fieldType should have exactly one anchor when no placeholders")
            assertEquals(templateString.trim(), result.first(), "$fieldType anchor should match original text")
        }
    }

    @Test
    fun `extractAnchors should handle template without placeholders`() {
        // Given
        val templateString = "Simple subject without placeholders"
        logger.info { "Original template string: '$templateString'" }
        
        // When
        val result = templateString.extractAnchors()
        logger.info { "Extracted anchors: $result" }
        
        // Then
        assertEquals(1, result.size, "Should have exactly one anchor")
        assertEquals(templateString, result.first(), "Anchor should match original text")
    }

    @Test
    fun `extractAnchors should handle template with multiple placeholders`() {
        // Given
        val templateString = "Hello {{name}}, your order {{orderId}} is ready"
        logger.info { "Original template string: '$templateString'" }
        
        // When
        val result = templateString.extractAnchors()
        logger.info { "Extracted anchors: $result" }
        
        // Then
        assertEquals(3, result.size, "Should have 3 anchors")
        assertEquals("Hello", result[0], "First anchor should be 'Hello'")
        assertEquals(", your order", result[1], "Second anchor should be ', your order'")
        assertEquals("is ready", result[2], "Third anchor should be 'is ready'")
    }

    @Test
    fun `extractAnchors should handle empty string`() {
        // Given
        val templateString = ""
        logger.info { "Original template string: '$templateString'" }
        
        // When
        val result = templateString.extractAnchors()
        logger.info { "Extracted anchors: $result" }
        
        // Then
        assertEquals(0, result.size, "Empty string should result in empty anchor list")
    }

    @Test
    fun `extractAnchors should handle email samples dataset`() {
        // Given
        val emails = emailDatasetProvider.loadEmails()
        assertTrue(emails.isNotEmpty(), "Emails should not be empty for testing")
        
        val templates = emailDatasetProvider.loadTemplates()
        assertTrue(templates.isNotEmpty(), "Templates should not be empty for testing")
        
        val templateMap = templates
        
        val random = Random(123) // Different seed for variety
        val selectedEmails = emails.shuffled(random).take(5)
        
        // When & Then
        selectedEmails.forEach { email ->
            val template = templateMap[email.templateId]
                ?: throw IllegalStateException("Template not found for templateId: ${email.templateId}")
            
            // Convert template to anchors using the new implementation
            val subjectAnchors = template.subject.extractAnchors()
            val snippetAnchors = template.snippet.extractAnchors()
            
            // Match real email with anchor patterns using EmailTemplateMatchService
            val matchService = EmailTemplateMatchService()
            val gmailMessage = GmailMessage(
                id = email.id,
                subject = email.subject,
                snippet = email.snippet,
                internalDate = Instant.now(),
                senderEmail = "test@exmaple.com",
                senderName = null,
            )
            
            val emailTemplate = EmailTemplate(
                subjectAnchors = subjectAnchors,
                snippetAnchors = snippetAnchors
            )
            
            val isMatch = matchService.matchMessage(emailTemplate, gmailMessage)
            
            assertTrue(isMatch) {
                """
                FAILED Match for Email ID: ${email.id} (templateId: ${email.templateId})
                --------------------------------------------------
                Email Subject: '${email.subject}'
                Subject Anchors: $subjectAnchors
                Email Snippet: '${email.snippet}'
                Snippet Anchors: $snippetAnchors
                --------------------------------------------------
                """.trimIndent()
            }
        }
    }

    @Test
    fun `extractAnchors should handle template with untrimmed anchors`() {
        // Given - Template with leading/trailing spaces that should be trimmed
        val templateString = "Hello {{name}} , your order {{orderId}} is ready"
        
        // When
        val result = templateString.extractAnchors()
        
        // Then - Anchors should be trimmed automatically
        assertEquals(3, result.size, "Should have 3 anchors")
        assertEquals("Hello", result[0], "First anchor should be trimmed to 'Hello'")
        assertEquals(", your order", result[1], "Second anchor should be trimmed to ', your order'")
        assertEquals("is ready", result[2], "Third anchor should be trimmed to 'is ready'")
    }

    @Test
    fun `extractAnchors should handle template with leading space anchor`() {
        // Given - Template that would produce anchor with leading space
        val templateString = " {{name}}Hello"
        
        // When
        val result = templateString.extractAnchors()
        
        // Then - Leading space should be trimmed
        assertEquals(1, result.size, "Should have 1 anchor")
        assertEquals("Hello", result[0], "Anchor should be trimmed to 'Hello'")
    }

    @Test
    fun `extractAnchors should handle template with trailing space anchor`() {
        // Given - Template that would produce anchor with trailing space
        val templateString = "Hello{{name}} "
        
        // When
        val result = templateString.extractAnchors()
        
        // Then - Trailing space should be trimmed
        assertEquals(1, result.size, "Should have 1 anchor")
        assertEquals("Hello", result[0], "Anchor should be trimmed to 'Hello'")
    }
}

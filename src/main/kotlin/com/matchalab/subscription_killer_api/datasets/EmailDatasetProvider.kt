package com.matchalab.subscription_killer_api.datasets

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.api.services.gmail.model.Message
import com.matchalab.subscription_killer_api.ai.toPromptParamString
import com.matchalab.subscription_killer_api.subscription.SubscriptionEventType
import com.matchalab.subscription_killer_api.utils.toGmailMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

private val logger = KotlinLogging.logger {}

/**
 * Component responsible for loading and providing email sample dataset
 */
@Component
class EmailDatasetProvider(
    private val objectMapper: ObjectMapper
) {
    
    private lateinit var emailSamples: List<EmailSample>
    
    /**
     * Loads email samples from the dataset file
     */
    @PostConstruct
    fun loadEmailSamples() {
        try {
            val resource = ClassPathResource("gmail-messages-sample.json")
            val jsonContent = resource.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            
            emailSamples = objectMapper.readValue(jsonContent)
            logger.info { "Loaded ${emailSamples.size} email samples" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to load email samples, using empty list" }
            emailSamples = emptyList()
        }
    }
    
    /**
     * Returns the loaded email samples
     */
    fun getEmailSamples(): List<EmailSample> = emailSamples
    
    /**
     * Returns the number of loaded samples
     */
    fun getSampleCount(): Int = emailSamples.size
    
    /**
     * Helper method to convert dataset to emailParams format
     * Uses loaded email samples to create GmailMessage and convert to prompt param string format
     */
    fun createEmailParamsFromDataset(): String {
        return emailSamples.mapIndexed { index, emailSample ->
            emailSample.message.toGmailMessage().toPromptParamString(index)
        }.joinToString("\n")
    }
}

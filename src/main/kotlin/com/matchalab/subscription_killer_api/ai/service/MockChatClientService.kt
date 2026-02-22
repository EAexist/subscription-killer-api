package com.matchalab.subscription_killer_api.ai.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.matchalab.subscription_killer_api.ai.parsePromptParamString
import com.matchalab.subscription_killer_api.datasets.EmailDatasetProvider
import com.matchalab.subscription_killer_api.datasets.EmailSample
import com.matchalab.subscription_killer_api.datasets.GmailApiMessage
import com.matchalab.subscription_killer_api.subscription.EmailTemplate
import com.matchalab.subscription_killer_api.subscription.SubscriptionEventType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
@Profile("!ai")
class MockChatClientService(
    private val datasetProvider: EmailDatasetProvider,
    private val mockChatResponseService: MockChatResponseService,
    private val objectMapper: ObjectMapper
) : ChatClientService {

    private lateinit var emailSamples: List<EmailSample>

    private fun <T> convertResultToJson(result: T): String {
        return objectMapper.writeValueAsString(result)
    }

    private fun GmailApiMessage.getSubject(): String {
        return this.payload.headers.find { it.name.equals("Subject", ignoreCase = true) }?.value ?: ""
    }

    override fun categorizeEmails(
        prompt: String,
        params: Map<String, String>,
        dataCount: Int?
    ): Map<String, List<Int>> {
        val messages = params["emails"]!!.parsePromptParamString()
        emailSamples = datasetProvider.getEmailSamples()

        // Create subject to subscription event type mapping from email samples
        val subjectToEventType = emailSamples.associateBy({ it.message.getSubject() }, { it.subscriptionEventType })

        // Categorize indices based on their subject's subscription event type
        val monthlySubscriptions = mutableListOf<Int>()
        val annualSubscriptions = mutableListOf<Int>()
        val subscriptionStarts = mutableListOf<Int>()
        val subscriptionCancellations = mutableListOf<Int>()

        messages.forEach { message ->
            val index = message["index"] as Int
            val subject = message["subject"] as String
            val snippet = message["snippet"] as String
            val eventType = subjectToEventType[subject]
            when (eventType) {
                SubscriptionEventType.MONTHLY_PAYMENT -> monthlySubscriptions.add(index)
                SubscriptionEventType.ANNUAL_PAYMENT -> annualSubscriptions.add(index)
                SubscriptionEventType.SUBSCRIPTION_START -> subscriptionStarts.add(index)
                SubscriptionEventType.SUBSCRIPTION_CANCEL -> subscriptionCancellations.add(index)
                SubscriptionEventType.UNKNOWN -> { /* ignore unknown types */
                }

                null -> { /* ignore subjects not found in sample data */
                }
            }
        }

        val result = mapOf(
            "M" to monthlySubscriptions,
            "A" to annualSubscriptions,
            "S" to subscriptionStarts,
            "C" to subscriptionCancellations,
        )

        val resultJson = convertResultToJson(result)

        // logger.debug { "[categorizeEmails] Result\n\tresultJson: $resultJson" }
        // {"M":[0,1,4,6],"A":[2],"S":[5,7],"C":[3]}

        mockChatResponseService.generateMockChatContext(
            taskName = "categorize_emails",
            prompt = prompt,
            params = params,
            outputFormattingString = "{\"M\":[],\"A\":[],\"S\":[],\"C\":[]}",
            dataCount = dataCount,
            outputString = resultJson
        )

        return result
    }

    override fun extractEmailTemplates(
        prompt: String,
        params: Map<String, String>,
        dataCount: Int?
    ): ExtractEmailTemplatesResponse {
        val messages = params["emails"]!!.parsePromptParamString()
        emailSamples = datasetProvider.getEmailSamples()
        val subjectToEmailTemplate = emailSamples.associateBy({ it.message.getSubject() }, { it.emailTemplate })

        val result: List<Map<String, String>> = messages.map { message ->
            val index = message["index"] as Int
            val subject = message["subject"] as String
            val snippet = message["snippet"] as String
            val template: EmailTemplate = subjectToEmailTemplate[subject] ?: return@map mapOf(
                "m" to index.toString(),
                "j" to "",
                "p" to ""
            )
            mapOf(
                "m" to index.toString(),
                "j" to template.subjectRegex,
                "p" to template.snippetRegex
            )
        }

        val extractResponse = ExtractEmailTemplatesResponse(result)

        val resultJson = convertResultToJson(extractResponse)

        logger.debug { "[extractEmailTemplates] Result\n\tresultJson: $resultJson" }
        // {"result":[]}

        mockChatResponseService.generateMockChatContext(
            taskName = "extract_email_templates",
            prompt = prompt,
            params = params,
            outputFormattingString = "{\"result\":[]}",
            dataCount = dataCount,
            outputString = resultJson
        )

        return extractResponse
    }
}
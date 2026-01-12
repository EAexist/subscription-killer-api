package com.matchalab.subscription_killer_api.evals.prompts

import com.google.api.services.gmail.model.Message
import com.matchalab.subscription_killer_api.ai.service.ChatClientService
import com.matchalab.subscription_killer_api.ai.service.ChatClientServiceImpl
import com.matchalab.subscription_killer_api.ai.service.call
import com.matchalab.subscription_killer_api.ai.service.config.PromptTemplateProperties
import com.matchalab.subscription_killer_api.config.SampleMessageConfig
import com.matchalab.subscription_killer_api.subscription.service.FilterAndCategorizeEmailsTaskResponse
import com.matchalab.subscription_killer_api.subscription.service.GmailMessageSummaryDto
import com.matchalab.subscription_killer_api.utils.toGmailMessage
import com.matchalab.subscription_killer_api.utils.toSummaryDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import kotlin.test.Test

private val logger = KotlinLogging.logger {}

@Tag("gcp")
@Tag("ai")
@SpringBootTest(
    classes = [
        ToolCallingAutoConfiguration::class,
        ChatClientServiceImpl::class,
        GoogleGenAiChatAutoConfiguration::class,
        ChatClientAutoConfiguration::class,
        SpringAiRetryAutoConfiguration::class,
    ],
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@Import(SampleMessageConfig::class)
@EnableConfigurationProperties(PromptTemplateProperties::class)
class FilterAndCategorizeEmailEval @Autowired constructor(
    private val chatClientService: ChatClientService,
    private val promptTemplateProperties: PromptTemplateProperties,
    private val sampleMessages: List<Message>
) {

    @Test
    fun `given gmail messages should identify message ids corresponding to subscription events`() {

        val emails: List<GmailMessageSummaryDto> = sampleMessages.mapNotNull { it.toGmailMessage()?.toSummaryDto() }

        val response: FilterAndCategorizeEmailsTaskResponse =
            chatClientService.call<FilterAndCategorizeEmailsTaskResponse>(
                promptTemplateProperties.filterAndCategorizeEmails,
                mapOf(
                    "emails" to emails
                )
            )

        logger.debug { "response: $response" }

        val expectedResponse = mapOf(
            "paymentStartMessages" to listOf(
                "19b633ef8816f70b", "198153271fe48cf1",
            ), "paymentCancelMessages" to listOf(
                "18fffe1927227419", "1901483a2036e020", "19b5eb36c656f8a3", "19b5eb7b6405f519", "195ee970f2954eb3",
                "19813abc202ea678", "19813abc3047adb3",
            ), "monthlyPaymentMessages" to listOf(
                "185becf0be8bdb2e",
                "1865e727c94cc0e0",
                "186eea2c4818cdaa",
                "1878e46c00da9d9c",
                "18828c7be8492286",
                "188c869cad94abb3",
                "18962e8d7d1a27bd",
                "18a028df5bb0c316",
                "18aa230a17350152",
                "18b3caf3134eb04e",
                "18c76d18829201b7",
                "18d167b6d08f876c",
                "18db61d55ee2d115",
                "18e4b75a73dc59d0",
                "18eeb1a9467cd67f",
                "18f8598119fb43e5",
            ), "annualPaymentMessages" to listOf(
            )
        )

        assertThat(response).isNotNull()
            .usingRecursiveComparison().isEqualTo(expectedResponse)
    }
}
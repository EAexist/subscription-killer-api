package com.matchalab.subscription_killer_api.evals.prompts

import com.matchalab.subscription_killer_api.ai.dto.EmailCategorizationResponse
import com.matchalab.subscription_killer_api.ai.dto.EmailTemplateExtractionResponse
import com.matchalab.subscription_killer_api.ai.dto.toEmailDetectionRuleGenerationDto
import com.matchalab.subscription_killer_api.ai.dto.toMessages
import com.matchalab.subscription_killer_api.ai.service.ChatClientServiceImpl
import com.matchalab.subscription_killer_api.ai.service.config.AiConfig
import com.matchalab.subscription_killer_api.ai.service.config.PromptTemplateProperties
import com.matchalab.subscription_killer_api.config.SampleMessageConfig
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.subscription.SubscriptionEventType
import com.matchalab.subscription_killer_api.subscription.config.MailProperties
import com.matchalab.subscription_killer_api.subscription.matchMessagesOrEmpty
import com.matchalab.subscription_killer_api.subscription.service.EmailDetectionRuleGenerationDto
import com.matchalab.subscription_killer_api.subscription.service.EmailTemplateExtractionPromptService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.assertAll
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test

private val logger = KotlinLogging.logger {}

@Tag("ai")
@SpringBootTest(
    classes = [
        ToolCallingAutoConfiguration::class,
        GoogleGenAiChatAutoConfiguration::class,
        ChatClientAutoConfiguration::class,
        SpringAiRetryAutoConfiguration::class,
        EmailTemplateExtractionPromptService::class,
        JacksonAutoConfiguration::class,
        ChatClientServiceImpl::class,
        AiConfig::class,
        SampleMessageConfig::class
    ],
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@EnableConfigurationProperties(PromptTemplateProperties::class, MailProperties::class)
class GeneralizeStringPatternEval @Autowired constructor(
    private val emailTemplateExtractionPromptService: EmailTemplateExtractionPromptService,
    private val sampleMessages: List<GmailMessage>,
) {

    @Test
    fun `given gmail messages should create general regex patterns for subscription event categories`() {

        val emailCategorizationResponse = EmailCategorizationResponse(
            listOf(
                "19b5eb4d89185432",
                "19b633ef8816f70b",
                "195bfcf179dd517a",
                "1905d62e1b5a4cc4",
                "1940d5a407867390",
                "1940e8e5a2e2c7d6",
                "197bc3a30d07e1a5",
                "198153271fe48cf1"
            ), listOf(
                "18fffe1927227419",
                "1901483a2036e020",
                "19b5eb36c656f8a3",
                "19b5eb7b6405f519",
                "195ee970f2954eb3",
                "190820cc8e9fd673",
                "1948ca756537adce",
                "19813abc202ea678",
                "19813abc3047adb3",
            ), listOf(
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
                "18bdc532da5dfead",
                "18c76d18829201b7",
                "18d167b6d08f876c",
                "18db61d55ee2d115",
                "18e4b75a73dc59d0",
                "18eeb1a9467cd67f",
                "18f8598119fb43e5",
            ), listOf<String>(
            )
        )

        val subscriptionEventMessages = emailCategorizationResponse.toMessages(sampleMessages)

        val exactResponse: EmailTemplateExtractionResponse =
            emailTemplateExtractionPromptService.run(subscriptionEventMessages)

        logger.debug { exactResponse.result.joinToString("\n") { "\n\t${it.messageIds}\n\t${it.template}" } }

        val emailDetectionRuleGenerationDtos: List<EmailDetectionRuleGenerationDto> =
            exactResponse.toEmailDetectionRuleGenerationDto(emailCategorizationResponse)

        logger.debug { emailDetectionRuleGenerationDtos.joinToString("\n") { "\n\t${it.eventType}\n\t${it.template}" } }

        assertAll(
            "Assertions",
            {
                val template =
                    emailDetectionRuleGenerationDtos.first { it.eventType == SubscriptionEventType.SUBSCRIPTION_START }.template
                assertThat(template.matchMessagesOrEmpty(sampleMessages).map { it.id })
                    .`as`("SUBSCRIPTION_START")
                    .containsExactlyInAnyOrderElementsOf(emailCategorizationResponse.subsStartMsgIds)
            },
            {
                val template =
                    emailDetectionRuleGenerationDtos.first { it.eventType == SubscriptionEventType.SUBSCRIPTION_CANCEL }.template
                assertThat(template.matchMessagesOrEmpty(sampleMessages).map { it.id })
                    .`as`("SUBSCRIPTION_CANCEL")
                    .containsExactlyInAnyOrderElementsOf(emailCategorizationResponse.subsCancelMsgIds)
            },
            {
                val template =
                    emailDetectionRuleGenerationDtos.first { it.eventType == SubscriptionEventType.MONTHLY_PAYMENT }.template
                assertThat(template.matchMessagesOrEmpty(sampleMessages).map { it.id })
                    .`as`("MONTHLY_PAYMENT")
                    .containsExactlyInAnyOrderElementsOf(emailCategorizationResponse.monthlyMsgIds)
            },
            {
                assertThat(emailDetectionRuleGenerationDtos.none { it.eventType == SubscriptionEventType.ANNUAL_PAYMENT })
                    .`as`("Should not contain an ANNUAL_PAYMENT template")
                    .isTrue()
            },
        )
    }
}




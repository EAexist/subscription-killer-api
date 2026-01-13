package com.matchalab.subscription_killer_api.evals.prompts

import com.matchalab.subscription_killer_api.ai.dto.EmailTemplateExtractionResponse
import com.matchalab.subscription_killer_api.ai.dto.toEmailDetectionRuleGenerationDto
import com.matchalab.subscription_killer_api.ai.dto.toMessages
import com.matchalab.subscription_killer_api.ai.service.ChatClientServiceImpl
import com.matchalab.subscription_killer_api.ai.service.config.AiConfig
import com.matchalab.subscription_killer_api.ai.service.config.PromptTemplateProperties
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.subscription.SubscriptionEventType
import com.matchalab.subscription_killer_api.subscription.config.MailProperties
import com.matchalab.subscription_killer_api.subscription.config.SampleMessageConfig
import com.matchalab.subscription_killer_api.subscription.matchMessagesOrEmpty
import com.matchalab.subscription_killer_api.subscription.service.EmailCategorizationResponseFactory
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

        val emailCategorizationResponse = EmailCategorizationResponseFactory.createSample()

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




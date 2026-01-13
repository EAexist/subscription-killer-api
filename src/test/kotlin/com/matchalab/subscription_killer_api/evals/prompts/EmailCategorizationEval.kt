package com.matchalab.subscription_killer_api.evals.prompts

import com.matchalab.subscription_killer_api.ai.dto.EmailCategorizationResponse
import com.matchalab.subscription_killer_api.ai.service.ChatClientServiceImpl
import com.matchalab.subscription_killer_api.ai.service.config.AiConfig
import com.matchalab.subscription_killer_api.ai.service.config.PromptTemplateProperties
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.subscription.config.MailProperties
import com.matchalab.subscription_killer_api.subscription.config.SampleMessageConfig
import com.matchalab.subscription_killer_api.subscription.service.EmailCategorizationPromptService
import com.matchalab.subscription_killer_api.subscription.service.EmailCategorizationResponseFactory
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
        EmailCategorizationPromptService::class,
        JacksonAutoConfiguration::class,
        ChatClientServiceImpl::class,
        AiConfig::class,
        SampleMessageConfig::class
    ],
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@EnableConfigurationProperties(PromptTemplateProperties::class, MailProperties::class)
class EmailCategorizationEval @Autowired constructor(
    private val emailCategorizationPromptService: EmailCategorizationPromptService,
    private val sampleMessages: List<GmailMessage>,
) {

    @Test
    fun `given gmail messages should identify message ids corresponding to subscription events`() {

        val exactResponse: EmailCategorizationResponse =
            emailCategorizationPromptService.run(sampleMessages)

        val expectedResponse = EmailCategorizationResponseFactory.createSample()

        assertAll(
            "Assertions",
            {
                assertThat(exactResponse.subsStartMsgIds)
                    .`as`("SUBSCRIPTION_START")
                    .containsExactlyInAnyOrderElementsOf(expectedResponse.subsStartMsgIds)
            },
            {
                assertThat(exactResponse.subsCancelMsgIds)
                    .`as`("SUBSCRIPTION_CANCEL")
                    .containsExactlyInAnyOrderElementsOf(expectedResponse.subsCancelMsgIds)
            },
            {
                assertThat(exactResponse.monthlyMsgIds)
                    .`as`("MONTHLY_PAYMENT")
                    .containsExactlyInAnyOrderElementsOf(expectedResponse.monthlyMsgIds)
            },
            {
                assertThat(exactResponse.annualMsgIds)
                    .`as`("ANNUAL_PAYMENT")
                    .containsExactlyInAnyOrderElementsOf(expectedResponse.annualMsgIds)
            }
        )
    }
}
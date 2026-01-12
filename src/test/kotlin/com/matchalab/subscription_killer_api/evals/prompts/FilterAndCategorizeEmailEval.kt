package com.matchalab.subscription_killer_api.evals.prompts

import com.google.api.services.gmail.model.Message
import com.matchalab.subscription_killer_api.ai.dto.FilterAndCategorizeEmailsResponse
import com.matchalab.subscription_killer_api.ai.service.ChatClientServiceImpl
import com.matchalab.subscription_killer_api.ai.service.config.AiConfig
import com.matchalab.subscription_killer_api.ai.service.config.PromptTemplateProperties
import com.matchalab.subscription_killer_api.config.SampleMessageConfig
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.subscription.service.EmailCategorizationPromptService
import com.matchalab.subscription_killer_api.utils.toGmailMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.assertAll
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

@Tag("ai")
@SpringBootTest(
    classes = [
        ToolCallingAutoConfiguration::class,
        GoogleGenAiChatAutoConfiguration::class,
        ChatClientAutoConfiguration::class,
        SpringAiRetryAutoConfiguration::class,
        EmailCategorizationPromptService::class,
        ChatClientServiceImpl::class,
        AiConfig::class
    ],
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@Import(
    SampleMessageConfig::class,
//    SmallerSampleMessageConfig::class
)
@EnableConfigurationProperties(PromptTemplateProperties::class)
class FilterAndCategorizeEmailEval @Autowired constructor(
    private val emailCategorizationPromptService: EmailCategorizationPromptService,
    private val promptTemplateProperties: PromptTemplateProperties,
    private val sampleMessages: List<Message>,
) {

    @Test
    fun `given gmail messages should identify message ids corresponding to subscription events`() {

        val allMessages: List<GmailMessage> =
            sampleMessages.mapNotNull { it.toGmailMessage() }
//                .filter {
//                it.senderEmail in listOf(
//                    "do-not-reply@watcha.com",
//                    "info@account.netflix.com",
//                    "message@adobe.com",
//                    "noreply_melon@kakaoent.com"
//                )
//            }

        val exactResponse: FilterAndCategorizeEmailsResponse =
            emailCategorizationPromptService.run(allMessages)

        val expectedResponse = FilterAndCategorizeEmailsResponse(
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
        assertAll(
            "Assertions",
            {
                assertThat(exactResponse.subscriptionStartMessageIds)
                    .`as`("PAID_SUBSCRIPTION_START")
                    .containsExactlyInAnyOrderElementsOf(expectedResponse.subscriptionStartMessageIds)
            },
            {
                assertThat(exactResponse.subscriptionCancelMessageIds)
                    .`as`("PAID_SUBSCRIPTION_CANCEL")
                    .containsExactlyInAnyOrderElementsOf(expectedResponse.subscriptionCancelMessageIds)
            },
            {
                assertThat(exactResponse.monthlyPaymentMessageIds)
                    .`as`("MONTHLY_PAYMENT")
                    .containsExactlyInAnyOrderElementsOf(expectedResponse.monthlyPaymentMessageIds)
            },
            {
                assertThat(exactResponse.annualPaymentMessageIds)
                    .`as`("ANNUAL_PAYMENT")
                    .containsExactlyInAnyOrderElementsOf(expectedResponse.annualPaymentMessageIds)
            }
        )

//        runBlocking {
//            allMessages.groupBy { it.senderEmail }.map { (se, messages) ->
//                async(Dispatchers.IO) {
//
//                    val exactResponse = chatClientService.call<FilterAndCategorizeEmailsResponse>(
//                        promptTemplateProperties.filterAndCategorizeEmails,
//                        mapOf("emails" to messages)
//                    )
//
//                    val expectedResponse = expectedResponses.mapValues { (_, ids) ->
//                        ids.filter { id -> idToMessages[id]?.senderEmail == se }.toSet()
//                    }
//
//                    assertAll(
//                        "Assertions for $se",
//                        {
//                            assertThat(exactResponse.subscriptionStartMessageIds.map { it.id }.toSet())
//                                .`as`("subscriptionStartMessages")
//                                .isEqualTo(expectedResponse["subscriptionStartMessages"] ?: emptySet<String>())
//                        },
//                        {
//                            assertThat(exactResponse.subscriptionCancelMessages.map { it.id }.toSet())
//                                .`as`("subscriptionCancelMessages")
//                                .isEqualTo(expectedResponse["subscriptionCancelMessages"] ?: emptySet<String>())
//                        },
//                        {
//                            assertThat(exactResponse.monthlyPaymentMessages.map { it.id }.toSet())
//                                .`as`("monthlyPaymentMessages")
//                                .isEqualTo(expectedResponse["monthlyPaymentMessages"] ?: emptySet<String>())
//                        },
//                        {
//                            assertThat(exactResponse.annualPaymentMessages.map { it.id }.toSet())
//                                .`as`("annualPaymentMessages")
//                                .isEqualTo(expectedResponse["annualPaymentMessages"] ?: emptySet<String>())
//                        }
//                    )
//                }
//            }.awaitAll()
//        }
    }
}
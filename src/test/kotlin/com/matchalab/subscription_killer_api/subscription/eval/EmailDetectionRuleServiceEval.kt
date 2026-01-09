package com.matchalab.subscription_killer_api.subscription.eval

import com.google.api.services.gmail.model.Message
import com.matchalab.subscription_killer_api.ai.service.ChatClientServiceImpl
import com.matchalab.subscription_killer_api.ai.service.config.PromptTemplateProperties
import com.matchalab.subscription_killer_api.config.SampleMessageConfig
import com.matchalab.subscription_killer_api.config.TestDataFactory
import com.matchalab.subscription_killer_api.subscription.service.EmailDetectionRuleService
import com.matchalab.subscription_killer_api.subscription.service.FilterAndCategorizeEmailsTaskResponse
import com.matchalab.subscription_killer_api.subscription.service.GmailMessageSummaryDto
import com.matchalab.subscription_killer_api.subscription.service.UpdateEmailDetectionRulesFromAIResultDto
import com.matchalab.subscription_killer_api.utils.toGmailMessage
import com.matchalab.subscription_killer_api.utils.toSummaryDto
import com.ninjasquad.springmockk.MockkBean
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.EntityManagerFactory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.BeforeEach
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
import org.springframework.test.context.ActiveProfiles
import kotlin.test.Test

private val logger = KotlinLogging.logger {}

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
//@EnableAutoConfiguration(
//    exclude = [
//        DataSourceAutoConfiguration::class,
//        HibernateJpaAutoConfiguration::class,
//        SecurityAutoConfiguration::class,
//        UserDetailsServiceAutoConfiguration::class,
//        SecurityFilterAutoConfiguration::class
//    ]
//)
@Import(EmailDetectionRuleService::class, SampleMessageConfig::class)
@EnableConfigurationProperties(PromptTemplateProperties::class)
@ActiveProfiles("dev", "test", "gcp")
@Tag("gcp")
@Tag("ai")
class EmailDetectionRuleServiceEval @Autowired constructor(
    private val emailDetectionRuleService: EmailDetectionRuleService,
    private val sampleMessages: List<Message>
) {
    @MockkBean
    lateinit var entityManagerFactory: EntityManagerFactory

    private val dataFactory = TestDataFactory()
    private val testEmailSource = dataFactory.createEmailSource("fakeEmailAddress", mutableMapOf())

    private lateinit var testMessageSummaries: List<GmailMessageSummaryDto>

    @BeforeEach
    fun setUp() {
        testMessageSummaries = sampleMessages.mapNotNull { it.toGmailMessage()?.toSummaryDto() }
    }

    @Test
    fun `given messages should prompt work well`() {
        val categorizedEmails: FilterAndCategorizeEmailsTaskResponse =
            emailDetectionRuleService.filterAndCategorizeEmails(emails = testMessageSummaries)

        logger.debug { "categorizedEmails: $categorizedEmails" }

        val proposedRules: UpdateEmailDetectionRulesFromAIResultDto =
            emailDetectionRuleService.generalizeStringPattern(categorizedEmails)

        logger.debug { "proposedRules: $proposedRules" }

        val mergedEmailDetectionRules: UpdateEmailDetectionRulesFromAIResultDto =
            emailDetectionRuleService.mergeEmailDetectionRules(testEmailSource, proposedRules)

        logger.debug { "mergedEmailDetectionRules: $mergedEmailDetectionRules" }

        assertAll(
            {
                assertAll(
                    "filterAndCategorizeEmails() Results",
                    { assertThat(categorizedEmails.paymentStartMessages).hasSize(1) },
                    { assertThat(categorizedEmails.paymentCancelMessages).hasSize(2) },
                    { assertThat(categorizedEmails.monthlyPaymentMessages).isEmpty() },
                    { assertThat(categorizedEmails.annualPaymentMessages).isEmpty() },
                    {
                        SoftAssertions.assertSoftly { softly ->
                            mapOf(
                                "Monthly Payment" to categorizedEmails.monthlyPaymentMessages,
                                "Annual Payment" to categorizedEmails.annualPaymentMessages,
                                "Refund Messages" to categorizedEmails.monthlyPaymentMessages,
                                "Spam Messages" to categorizedEmails.annualPaymentMessages
                            ).forEach { (_, messages) ->
                                softly.assertThat(messages).isEmpty()
                            }
                        }
                    }
                )
            },

            {
                assertAll(
                    "generalizeStringPattern() Results",
                    { assertThat(proposedRules.paymentStartRule).isNotNull() },
                    { assertThat(proposedRules.paymentCancelRule).isNotNull() },
                    { assertThat(proposedRules.monthlyPaymentRule).isNull() },
                    { assertThat(proposedRules.annualPaymentRule).isNull() },
                    {
                        SoftAssertions.assertSoftly { softly ->
                            mapOf(
                                "Monthly Payment" to proposedRules.monthlyPaymentRule,
                                "Annual Payment" to proposedRules.annualPaymentRule,
                                "Refund Messages" to proposedRules.monthlyPaymentRule,
                                "Spam Messages" to proposedRules.annualPaymentRule
                            ).forEach { (_, messages) ->
                                softly.assertThat(messages).isNull()
                            }
                        }
                    }
                )
            },

            {
                assertThat(proposedRules).isEqualTo(mergedEmailDetectionRules)
            }
        )
    }
}
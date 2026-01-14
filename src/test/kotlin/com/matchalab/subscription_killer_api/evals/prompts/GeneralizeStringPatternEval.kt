package com.matchalab.subscription_killer_api.evals.prompts

import com.matchalab.subscription_killer_api.ai.dto.EmailTemplateExtractionResponse
import com.matchalab.subscription_killer_api.ai.dto.toEmailDetectionRuleGenerationDto
import com.matchalab.subscription_killer_api.ai.dto.toMessages
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.subscription.SubscriptionEventType
import com.matchalab.subscription_killer_api.subscription.matchMessagesOrEmpty
import com.matchalab.subscription_killer_api.subscription.service.EmailCategorizationResponseFactory
import com.matchalab.subscription_killer_api.subscription.service.EmailDetectionRuleGenerationDto
import com.matchalab.subscription_killer_api.subscription.service.EmailTemplateExtractionPromptService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test

private val logger = KotlinLogging.logger {}

@Tag("ai")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureObservability
class GeneralizeStringPatternEval @Autowired constructor(
    private val emailTemplateExtractionPromptService: EmailTemplateExtractionPromptService,
    private val sampleMessages: List<GmailMessage>,
    private val observationRegistry: ObservationRegistry
) {

    @Test
    fun `given gmail messages should create general regex patterns for subscription event categories`() {

        val allExpectedEmailCategorizationResponse = EmailCategorizationResponseFactory.createSample()

        val emailCategorizationResponse = EmailCategorizationResponseFactory.createUniqueSample()

        val subscriptionEventMessages = emailCategorizationResponse.toMessages(sampleMessages)

        lateinit var exactResponse: EmailTemplateExtractionResponse

        Observation.createNotStarted("EmailCategorizationEval", observationRegistry).observe {
            exactResponse =
                emailTemplateExtractionPromptService.run(subscriptionEventMessages)
        }

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
                    .containsExactlyInAnyOrderElementsOf(allExpectedEmailCategorizationResponse.subsStartMsgIds)
            },
            {
                val template =
                    emailDetectionRuleGenerationDtos.first { it.eventType == SubscriptionEventType.SUBSCRIPTION_CANCEL }.template
                assertThat(template.matchMessagesOrEmpty(sampleMessages).map { it.id })
                    .`as`("SUBSCRIPTION_CANCEL")
                    .containsExactlyInAnyOrderElementsOf(allExpectedEmailCategorizationResponse.subsCancelMsgIds)
            },
            {
                val template =
                    emailDetectionRuleGenerationDtos.first { it.eventType == SubscriptionEventType.MONTHLY_PAYMENT }.template
                assertThat(template.matchMessagesOrEmpty(sampleMessages).map { it.id })
                    .`as`("MONTHLY_PAYMENT")
                    .containsExactlyInAnyOrderElementsOf(allExpectedEmailCategorizationResponse.monthlyMsgIds)
            },
            {
                assertThat(emailDetectionRuleGenerationDtos.none { it.eventType == SubscriptionEventType.ANNUAL_PAYMENT })
                    .`as`("Should not contain an ANNUAL_PAYMENT template")
                    .isTrue()
            },
        )
    }
}




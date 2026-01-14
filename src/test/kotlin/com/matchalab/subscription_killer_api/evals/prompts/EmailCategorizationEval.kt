package com.matchalab.subscription_killer_api.evals.prompts

import com.matchalab.subscription_killer_api.ai.dto.EmailCategorizationResponse
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.subscription.service.EmailCategorizationPromptService
import com.matchalab.subscription_killer_api.subscription.service.EmailCategorizationResponseFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertNotNull
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
class EmailCategorizationEval @Autowired constructor(
    private val emailCategorizationPromptService: EmailCategorizationPromptService,
    private val sampleMessages: List<GmailMessage>,
    private val observationRegistry: ObservationRegistry
) {
    @Test
    fun verifyObservability() {
        assertNotNull(observationRegistry)
    }

    @Test
    fun `given gmail messages should identify message ids corresponding to subscription events`() {
        lateinit var exactResponse: EmailCategorizationResponse

        Observation.createNotStarted("EmailCategorizationEval", observationRegistry).observe {
            exactResponse =
                emailCategorizationPromptService.run(sampleMessages)
        }
        val expectedResponse = EmailCategorizationResponseFactory.createUniqueSample()
        val allowedIds = EmailCategorizationResponseFactory.createAllowedSamples()

        assertAll(
            "Assertions",
            {
                assertThat(exactResponse.subsStartMsgIds.filter { it !in allowedIds.subsStartMsgIds })
                    .`as`("SUBSCRIPTION_START")
                    .containsExactlyInAnyOrderElementsOf(expectedResponse.subsStartMsgIds)
            },
            {
                assertThat(exactResponse.subsCancelMsgIds.filter { it !in allowedIds.subsCancelMsgIds })
                    .`as`("SUBSCRIPTION_CANCEL")
                    .containsExactlyInAnyOrderElementsOf(expectedResponse.subsCancelMsgIds)
            },
            {
                assertThat(exactResponse.monthlyMsgIds.filter { it !in allowedIds.monthlyMsgIds })
                    .`as`("MONTHLY_PAYMENT")
                    .containsExactlyInAnyOrderElementsOf(expectedResponse.monthlyMsgIds)
            },
            {
                assertThat(exactResponse.annualMsgIds.filter { it !in allowedIds.annualMsgIds })
                    .`as`("ANNUAL_PAYMENT")
                    .containsExactlyInAnyOrderElementsOf(expectedResponse.annualMsgIds)
            }
        )
    }
}
package com.matchalab.subscription_killer_api.ai.observation

import com.matchalab.subscription_killer_api.ai.service.prompt.emailcategorization.EmailCategorizationPromptService
import com.matchalab.subscription_killer_api.ai.service.prompt.emailtemplateextraction.EmailTemplateExtractionPromptService
import com.matchalab.subscription_killer_api.config.DatasetTestConfiguration
import com.matchalab.subscription_killer_api.config.ObservationTestConfig
import com.matchalab.subscription_killer_api.datasets.EmailDatasetProvider
import com.matchalab.subscription_killer_api.datasets.EmailSample
import io.micrometer.observation.tck.TestObservationRegistry
import io.micrometer.observation.tck.TestObservationRegistryAssert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@Import(DatasetTestConfiguration::class)
abstract class AbstractTokenMonitoringObservationFilterTest {

    @Autowired
    lateinit var emailCategorizationPromptService: EmailCategorizationPromptService

    @Autowired
    lateinit var emailTemplateExtractionPromptService: EmailTemplateExtractionPromptService

    @Autowired
    lateinit var testObservationRegistry: TestObservationRegistry

    @Autowired
    lateinit var emailDatasetProvider: EmailDatasetProvider
    protected lateinit var emailSamples: List<EmailSample>

    @BeforeEach
    fun setUp() {
        emailSamples = emailDatasetProvider.getEmailSamples()
        testObservationRegistry.clear()
    }

    @AfterEach
    fun clear() {
        testObservationRegistry.clear()
    }

    companion object {
        @JvmStatic
        fun aiServiceTestProvider(): List<AiServiceTest> {
            return listOf(
                AiServiceTest("emailCategorization", "Email Categorization"),
                AiServiceTest("emailTemplateExtraction", "Email Template Extraction")
            )
        }
    }

    data class AiServiceTest(
        val serviceName: String,
        val displayName: String
    )

    @ParameterizedTest
    @MethodSource("aiServiceTestProvider")
    fun `given AI Services when run called should record observation`(testData: AiServiceTest) {
        val messagesWithSubscriptionEventType =
            emailDatasetProvider.getSampleMessagesWithSubscriptionEventType()

        when (testData.serviceName) {
            "emailCategorization" -> {
                emailCategorizationPromptService.run(messagesWithSubscriptionEventType.map { it.first })
            }

            "emailTemplateExtraction" -> {
                emailTemplateExtractionPromptService.run(messagesWithSubscriptionEventType)
            }
        }

        TestObservationRegistryAssert.assertThat(testObservationRegistry)
            .doesNotHaveAnyRemainingCurrentObservation()
            .hasObservationWithNameEqualTo("app chat_client_service call")
            .that()
            .hasBeenStarted()
            .hasBeenStopped()
            .hasLowCardinalityKeyValueWithKey("app.ai.task_name")
            .hasHighCardinalityKeyValueWithKey("app.ai.data_count")
            .hasLowCardinalityKeyValueWithKey("gen_ai.request.model")
            .hasHighCardinalityKeyValueWithKey("gen_ai.usage.input_tokens")
            .hasHighCardinalityKeyValueWithKey("gen_ai.usage.output_tokens")
            .hasHighCardinalityKeyValueWithKey("gen_ai.usage.total_tokens")
            .hasHighCardinalityKeyValueWithKey("gen_ai.usage.instruction_tokens")
            .hasHighCardinalityKeyValueWithKey("gen_ai.usage.input_tokens_per_item")
            .hasHighCardinalityKeyValueWithKey("gen_ai.usage.output_tokens_per_item")
    }
}

@SpringBootTest
@AutoConfigureObservability
@Import(ObservationTestConfig::class)
@ActiveProfiles("test", "ai")
@Tag("ai")
class ChatClientServiceImplTokenMonitoringIT : AbstractTokenMonitoringObservationFilterTest()

@SpringBootTest
@AutoConfigureObservability
@Import(ObservationTestConfig::class)
class MockChatClientServiceTokenMonitoringIT : AbstractTokenMonitoringObservationFilterTest()

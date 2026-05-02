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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@AutoConfigureObservability
@ActiveProfiles("test", "benchmark")
@Import(ObservationTestConfig::class, DatasetTestConfiguration::class)
class LangfuseObservationFilterIT {

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
    fun `should map all observation keys from app to langfuse and OTEL conventions`(testData: AiServiceTest) {
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
            // Token Usage Mappings
            .hasHighCardinalityKeyValueWithKey("gen_ai.usage.input_tokens")
            .hasHighCardinalityKeyValueWithKey("gen_ai.usage.output_tokens")
            .hasHighCardinalityKeyValueWithKey("gen_ai.usage.total_tokens")
            // Pass-through OTEL Semantic Conventions
//            .hasHighCardinalityKeyValueWithKey("gen_ai.usage.thinking_tokens")
//            .hasHighCardinalityKeyValueWithKey("gen_ai.usage.cached_tokens")
            // Custom Token Usage Mappings
            .hasHighCardinalityKeyValueWithKey("langfuse.observation.metadata.data_count")
            .hasHighCardinalityKeyValueWithKey("gen_ai.usage.instruction_tokens")
            .hasHighCardinalityKeyValueWithKey("gen_ai.usage.input_tokens_per_item")
            .hasHighCardinalityKeyValueWithKey("gen_ai.usage.output_tokens_per_item")
            // --- DIMENSIONAL DATA (Low Cardinality - for Aggregated Dashboard Plots) ---
            .hasLowCardinalityKeyValueWithKey("gen_ai.request.model") // REQUIRED for Cost Analytics
            .hasLowCardinalityKeyValueWithKey("langfuse.observation.metadata.task_name")
//            .hasLowCardinalityKeyValueWithKey("langfuse.user.id") // REQUIRED for User Analytics
//            .hasLowCardinalityKeyValueWithKey("langfuse.tags") // For "Environment" (prod/dev) plots
    }
}


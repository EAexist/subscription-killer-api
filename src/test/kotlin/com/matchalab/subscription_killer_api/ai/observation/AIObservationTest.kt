package com.matchalab.subscription_killer_api.ai.observation

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.matchalab.subscription_killer_api.ai.service.ChatClientService
import com.matchalab.subscription_killer_api.config.ObservationTestConfig
import com.matchalab.subscription_killer_api.datasets.EmailDatasetProvider
import com.matchalab.subscription_killer_api.datasets.EmailSample
import io.micrometer.observation.tck.TestObservationRegistry
import io.micrometer.observation.tck.TestObservationRegistryAssert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

abstract class AbstractChatClientObservationTest {

    @Autowired
    lateinit var service: ChatClientService

    @Autowired
    lateinit var testObservationRegistry: TestObservationRegistry

    protected lateinit var datasetProvider: EmailDatasetProvider
    protected lateinit var emailSamples: List<EmailSample>

    @BeforeEach
    fun setUp() {
        val objectMapper = ObjectMapper().registerKotlinModule()
        datasetProvider = EmailDatasetProvider(objectMapper)
        datasetProvider.loadEmailSamples()
        emailSamples = datasetProvider.getEmailSamples()
        testObservationRegistry.clear()
    }

    @AfterEach
    fun clear() {
        testObservationRegistry.clear()
    }

    @Test
    fun `given ChatClientService when categorizeEmails called should record observation`() {
//        val observation = Observation.createNotStarted("chat_client_service_test", testObservationRegistry)
//            .contextualName("chatClientServiceTest")

//        observation.observe {
        val prompt = "Test categorization prompt"
        val emailParams = datasetProvider.createEmailParamsFromDataset()
        val params = mapOf("emails" to emailParams)
        val dataCount = emailSamples.size

        val result = service.categorizeEmails(prompt, params, dataCount)

        assert(result.isNotEmpty())

        TestObservationRegistryAssert.assertThat(testObservationRegistry)
            .doesNotHaveAnyRemainingCurrentObservation()
            .hasObservationWithNameEqualTo("app chat_client_service call")
            .that()
            .hasBeenStarted()
            .hasBeenStopped()
            .hasLowCardinalityKeyValueWithKey("app.ai.usage.input_tokens")
            .hasLowCardinalityKeyValueWithKey("app.ai.usage.output_tokens")
            .hasLowCardinalityKeyValueWithKey("app.ai.usage.total_tokens")
            .hasLowCardinalityKeyValueWithKey("app.ai.data_count")
            .hasLowCardinalityKeyValueWithKey("app.ai.usage.instruction_tokens")
            .hasLowCardinalityKeyValueWithKey("app.ai.usage.input_tokens_per_item")
            .hasLowCardinalityKeyValueWithKey("app.ai.usage.output_tokens_per_item")
    }
}

@SpringBootTest
@AutoConfigureObservability
@Import(ObservationTestConfig::class)
@ActiveProfiles("test", "ai")
@Tag("ai")
class ChatClientServiceImplObservationTest : AbstractChatClientObservationTest()

@SpringBootTest
@AutoConfigureObservability
@Import(ObservationTestConfig::class)
class MockChatClientServiceObservationTest : AbstractChatClientObservationTest()

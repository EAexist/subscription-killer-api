package com.matchalab.sublog_api.subscription.controller

import com.matchalab.sublog_api.config.AuthenticatedClientFactory
import com.matchalab.sublog_api.config.DatabaseTestUtils
import com.matchalab.sublog_api.config.SharedTestcontainersConfig
import com.matchalab.sublog_api.subscription.progress.AnalysisProgressStatus
import com.matchalab.sublog_api.subscription.progress.dto.AnalysisProgressUpdate
import com.matchalab.sublog_api.subscription.progress.dto.AppUserAnalysisProgressUpdate
import com.matchalab.sublog_api.subscription.progress.dto.ServiceProviderAnalysisProgressUpdate
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.context.WebApplicationContext
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Duration
import java.util.*


private val logger = KotlinLogging.logger {}

@Tag("oauth")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@AutoConfigureObservability
@Import(
    AuthenticatedClientFactory::class,
    SharedTestcontainersConfig::class,
    DatabaseTestUtils::class
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SubscriptionControllerIT
@Autowired
constructor(
    private val authenticatedClientFactory: AuthenticatedClientFactory,
) {
    private lateinit var authedClient: WebTestClient
    private lateinit var testAppUserId: UUID

    @Autowired
    lateinit var webTestClient: WebTestClient

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    lateinit var context: WebApplicationContext

    @BeforeEach
    fun setUp() {
        val authenticatedClientSetup = authenticatedClientFactory.create(port)
        testAppUserId = authenticatedClientSetup.appUserId
        authedClient = authenticatedClientSetup.client
    }

    @AfterEach
    fun tearDown() {
        authenticatedClientFactory.clear()
    }

    @Test
    fun `when subscribed analysis server-sent event should return progress`() = runTest {

        // When, Then
        authedClient
            .post()
            .uri("/api/v1/reports/updates")
            .exchange()
            .expectStatus().isAccepted

        val eventStream: Flux<AnalysisProgressUpdate> = authedClient.mutate()
            .responseTimeout(Duration.ofSeconds(30)).build().get()
            .uri("/api/v1/reports/updates")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .returnResult(object : ParameterizedTypeReference<AnalysisProgressUpdate>() {})
            .responseBody.doOnNext { logger.debug { "🔊 | eventStream: $it" } }

        StepVerifier.create(eventStream).expectSubscription()
            .recordWith { ArrayList() } // Collect what we catch
            .thenConsumeWhile { it is AppUserAnalysisProgressUpdate || it is ServiceProviderAnalysisProgressUpdate }
            .consumeRecordedWith { results ->
                val statuses =
                    results.filterIsInstance<AppUserAnalysisProgressUpdate>().map { it.status }

                // Assert that at least the final state is reached
                assertThat(statuses).contains(AnalysisProgressStatus.COMPLETED)

                // Assert that the states we DID catch are in the correct relative order
                // (e.g., STARTED cannot come AFTER COMPLETED)
                assertThat(statuses).isSortedAccordingTo(Comparator.comparing { it.ordinal })
            }
            .expectComplete()
            .verify(Duration.ofSeconds(600))
    }

}

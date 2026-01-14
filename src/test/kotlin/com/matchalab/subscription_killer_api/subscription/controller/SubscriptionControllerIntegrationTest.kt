package com.matchalab.subscription_killer_api.subscription.controller

import com.matchalab.subscription_killer_api.config.AuthenticatedClientFactory
import com.matchalab.subscription_killer_api.config.SampleGoogleAccountProperties
import com.matchalab.subscription_killer_api.repository.AppUserRepository
import com.matchalab.subscription_killer_api.repository.ServiceProviderRepository
import com.matchalab.subscription_killer_api.subscription.Subscription
import com.matchalab.subscription_killer_api.subscription.dto.SubscriptionReportResponseDto
import com.matchalab.subscription_killer_api.subscription.progress.AnalysisProgressStatus
import com.matchalab.subscription_killer_api.subscription.progress.dto.AnalysisProgressUpdate
import com.matchalab.subscription_killer_api.subscription.progress.dto.AppUserAnalysisProgressUpdate
import com.matchalab.subscription_killer_api.subscription.progress.dto.ServiceProviderAnalysisProgressUpdate
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.EntityNotFoundException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.transaction.support.TransactionTemplate
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Duration
import java.time.Instant
import java.util.*


private val logger = KotlinLogging.logger {}

@Tag("google-auth")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@EnableConfigurationProperties(SampleGoogleAccountProperties::class)
@AutoConfigureObservability
@Import(AuthenticatedClientFactory::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SubscriptionControllerIntegrationTest
@Autowired
constructor(
    private val appUserRepository: AppUserRepository,
    private val serviceProviderRepository: ServiceProviderRepository,
    private val transactionTemplate: TransactionTemplate,
    private val authenticatedClientFactory: AuthenticatedClientFactory,
) {
    private lateinit var sampleAppUserId: UUID
    private lateinit var authedClient: WebTestClient

    @LocalServerPort
    private var port: Int = 0

    @BeforeEach
    fun setUp() {
        appUserRepository.deleteAll()
        val authenticatedClientSetup = authenticatedClientFactory.create(port)
        sampleAppUserId = authenticatedClientSetup.appUserId
        authedClient = authenticatedClientSetup.client
    }

    @AfterEach
    fun tearDown() {
        appUserRepository.deleteAll()
    }

    @Test
    fun `when valid analysis request should trigger async pipeline and return 202 Accepted`() {

        // When, Then
        authedClient
            .post()
            .uri("/api/v1/reports/updates")
            .exchange()
            .expectStatus()
            .isAccepted()
    }

    @Test
    fun `when subscribed analysis server-sent event should return progress`() = runTest {

        // When, Then
        authedClient.post()
            .uri("/api/v1/reports/updates")
            .exchange()
            .expectStatus().isAccepted

        val eventStream: Flux<AnalysisProgressUpdate> = authedClient.get()
            .uri("/api/v1/reports/updates")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .returnResult(object : ParameterizedTypeReference<AnalysisProgressUpdate>() {})
            .responseBody.doOnNext { logger.debug { "🔊 | eventStream: $it" } }

        StepVerifier.create(eventStream).expectSubscription()
            .assertNext { update ->
                assertThat(update).isInstanceOf(AppUserAnalysisProgressUpdate::class.java)
                val appUpdate = update as AppUserAnalysisProgressUpdate
                assertThat(appUpdate.status).isEqualTo(AnalysisProgressStatus.STARTED)
            }
            .assertNext { update ->
                assertThat(update).isInstanceOf(AppUserAnalysisProgressUpdate::class.java)
                val appUpdate = update as AppUserAnalysisProgressUpdate
                assertThat(appUpdate.status).isEqualTo(AnalysisProgressStatus.EMAIL_FETCHED)
            }
            .thenConsumeWhile { update ->
                update is ServiceProviderAnalysisProgressUpdate
            }
            .assertNext { update ->
                assertThat(update).isInstanceOf(AppUserAnalysisProgressUpdate::class.java)
                val appUpdate = update as AppUserAnalysisProgressUpdate
                assertThat(appUpdate.status).isEqualTo(AnalysisProgressStatus.EMAIL_ACCOUNT_ANALYSIS_COMPLETED)
            }
            .assertNext { update ->
                assertThat(update).isInstanceOf(AppUserAnalysisProgressUpdate::class.java)
                val appUpdate = update as AppUserAnalysisProgressUpdate
                assertThat(appUpdate.status).isEqualTo(AnalysisProgressStatus.COMPLETED)
            }
            .expectComplete()
            .verify(Duration.ofSeconds(120))

        println("Test finished, waiting for Zipkin flush...")
        delay(5000)
    }

    @Test
    fun `given analyzed subscriptions when request subscription report should return report`() {

        // Given
        transactionTemplate.execute {
            val appUser = appUserRepository.findById(sampleAppUserId)
                .orElseThrow { EntityNotFoundException("🚨 User not found with id $sampleAppUserId") }

            val serviceProvider =
                serviceProviderRepository.findByDisplayName("Netflix") ?: throw EntityNotFoundException(
                    "🚨 ServiceProvider not found with displayName Netflix"
                )

            appUser.googleAccounts.forEach {

                val subscription =
                    Subscription(
                        null,
                        Instant.now(),
                        false,
                        Instant.now(),
                        false,
                        serviceProvider,
                        it
                    )
                it.updateSubscriptions(listOf(subscription))
                it.analyzedAt = Instant.now()
            }

            appUserRepository.save(appUser)
        }
        // When, Then
        authedClient
            .get()
            .uri("/api/v1/reports")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody<SubscriptionReportResponseDto>()
            .consumeWith { it ->
                val body = it.responseBody
                assertThat(body).isNotNull()
                assertThat(body?.accountReports).isNotEmpty()

                logger.debug { "\uD83D\uDE80 | /api/v1/reports Response body: $body" }
            }
    }

    @Test
    fun `given never analyzed subscriptions when request subscription report should return report`() {

        // When, Then
        authedClient
            .get()
            .uri("/api/v1/reports")
            .exchange()
            .expectStatus()
            .isNoContent()
            .expectBody().isEmpty()
    }
}

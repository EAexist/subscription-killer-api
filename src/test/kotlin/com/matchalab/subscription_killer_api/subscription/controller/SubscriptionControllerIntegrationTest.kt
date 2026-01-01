package com.matchalab.subscription_killer_api.subscription.controller

import com.matchalab.subscription_killer_api.config.GoogleTestUserProperties
import com.matchalab.subscription_killer_api.config.TestDataFactory
import com.matchalab.subscription_killer_api.domain.AppUser
import com.matchalab.subscription_killer_api.domain.GoogleAccount
import com.matchalab.subscription_killer_api.domain.UserRoleType
import com.matchalab.subscription_killer_api.repository.AppUserRepository
import com.matchalab.subscription_killer_api.repository.ServiceProviderRepository
import com.matchalab.subscription_killer_api.security.CustomOidcUser
import com.matchalab.subscription_killer_api.subscription.Subscription
import com.matchalab.subscription_killer_api.subscription.analysisStep.AnalysisProgressStatusDto
import com.matchalab.subscription_killer_api.subscription.analysisStep.AnalysisStatusType
import com.matchalab.subscription_killer_api.subscription.dto.SubscriptionAnalysisResponseDto
import com.matchalab.subscription_killer_api.subscription.dto.SubscriptionReportResponseDto
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.EntityNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.session.Session
import org.springframework.session.SessionRepository
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.test.web.reactive.server.returnResult
import org.springframework.transaction.support.TransactionTemplate
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Duration
import java.time.Instant
import java.util.*


private val logger = KotlinLogging.logger {}

//@Tag("ai")
//@Tag("gcp")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@EnableConfigurationProperties(GoogleTestUserProperties::class)
class SubscriptionControllerIntegrationTest
@Autowired
constructor(
    private val appUserRepository: AppUserRepository,
    private val serviceProviderRepository: ServiceProviderRepository,
    private val sessionRepository: SessionRepository<out Session>,
    private val webTestClient: WebTestClient,
    private val googleTestUserProperties: GoogleTestUserProperties,
    private val transactionTemplate: TransactionTemplate,
) {
    @Autowired
//    lateinit var googleTestUserProperties: GoogleTestUserProperties

    lateinit var authedClient: WebTestClient

    private val testDataFactory = TestDataFactory(serviceProviderRepository)

    private val localhost = "https://localhost:3000"

    private val testUserName: String = "testUserName"

    private lateinit var testAppUserId: UUID

    @BeforeEach
    fun setUp() {

        val testAppUser =
            AppUser(
                null,
                testUserName,
                UserRoleType.USER,
                mutableListOf<GoogleAccount>()
            )
        testAppUser.addGoogleAccount(
            GoogleAccount(
                googleTestUserProperties.subject,
                testUserName,
                "testUserEmail",
                googleTestUserProperties.refreshToken,
                googleTestUserProperties.accessToken,
                googleTestUserProperties.expiresAt,
                googleTestUserProperties.scope
            )
        )

        testAppUserId = checkNotNull(appUserRepository.saveAndFlush(testAppUser).id) {
            "🚨 Exception: testAppUserId is null."
        }

        val principal: OidcUser = CustomOidcUser(testAppUserId, listOf(SimpleGrantedAuthority("ROLE_USER")))
        val auth = OAuth2AuthenticationToken(
            principal, principal.authorities, "google"
        )

        @Suppress("UNCHECKED_CAST")
        val repo = sessionRepository as SessionRepository<Session>
        val session = repo.createSession()
        val context: SecurityContext = SecurityContextHolder.createEmptyContext().apply {
            authentication = auth
        }

        session.setAttribute("SPRING_SECURITY_CONTEXT", context)
        val encodedSessionId = Base64.getEncoder().encodeToString(session.id.toByteArray())
        repo.save(session)

        if (!appUserRepository.existsById(testAppUserId)) {
            throw IllegalStateException("🚨 Setup failed: Data not found in DB before request")
        }
        if (repo.findById(session.id) == null) {
            throw IllegalStateException("🚨 Exception: Data not found in setup")
        }

        authedClient = webTestClient.mutate()
            .defaultHeader(HttpHeaders.ORIGIN, localhost)
            .defaultCookie("SESSION", encodedSessionId)
            .responseTimeout(Duration.ofSeconds(30))
            .build()
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
            .uri("/api/v1/reports/analysis")
            .exchange()
            .expectStatus()
            .isAccepted()
            .expectBody<SubscriptionAnalysisResponseDto>().isEqualTo(SubscriptionAnalysisResponseDto())
    }

    @Test
    fun `when subscribed analysis server-sent event should return progress`() {

        // When, Then
        val result = authedClient.get()
            .uri("/api/v1/reports/analysis/progress")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .returnResult<AnalysisProgressStatusDto>()
//            .responseBody.doOnNext { logger.debug { "\uD83D\uDE80 [Flux Event] $it" } }

        val eventStream: Flux<AnalysisProgressStatusDto> = result.responseBody.doOnNext { status ->
            println("\uD83D\uDC1E Received status: $status")
        }

        StepVerifier.create(eventStream).expectSubscription()
            .then {
                authedClient.post()
                    .uri("/api/v1/reports/analysis")
                    .exchange()
                    .expectStatus().isAccepted
            }
//            .assertNext { assertThat(it.type).isEqualTo(AnalysisStatusType.STARTED) }
            .assertNext { assertThat(it.type).isEqualTo(AnalysisStatusType.EMAIL_FETCHED) }
            .thenConsumeWhile { it.type == AnalysisStatusType.SERVICE_PROVIDER_ANALYSIS_COMPLETED }
            .assertNext { assertThat(it.type).isEqualTo(AnalysisStatusType.COMPLETED) }
            .thenCancel()
            .verify(Duration.ofSeconds(20))
    }

    @Test
    fun `given analyzed subscriptions when request subscription report should return report`() {

        // Given
        transactionTemplate.execute {
            val appUser = appUserRepository.findById(testAppUserId)
                .orElseThrow { EntityNotFoundException("🚨 User not found with id $testAppUserId") }

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

                logger.debug { "\uD83D\uDE80 /api/v1/reports Response body: $body" }
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

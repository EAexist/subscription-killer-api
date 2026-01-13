package com.matchalab.subscription_killer_api.perf

import com.matchalab.subscription_killer_api.config.SampleGoogleAccountProperties
import com.matchalab.subscription_killer_api.config.SmallerSampleMessageConfig
import com.matchalab.subscription_killer_api.repository.AppUserRepository
import com.matchalab.subscription_killer_api.repository.EmailSourceRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.awaitility.kotlin.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.transaction.support.TransactionTemplate
import java.util.*
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

@Tag("ai")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableAutoConfiguration
@AutoConfigureWebTestClient
@EnableConfigurationProperties(SampleGoogleAccountProperties::class)
@AutoConfigureObservability
@Import(
    AuthenticatedClientFactory::class,
    SmallerSampleMessageConfig::class,
)
class AITokenReportTest
@Autowired
constructor(
    private val authenticatedClientFactory: AuthenticatedClientFactory,
    private val emailSourceRepository: EmailSourceRepository,
    private val transactionTemplate: TransactionTemplate,
    private val appUserRepository: AppUserRepository,
) {
    private lateinit var sampleAppUserId: UUID
    private lateinit var authedClient: WebTestClient

    @LocalServerPort
    private var port: Int = 0

    @BeforeEach
    fun setUp() {
        emailSourceRepository.clearAllEventRules()
        val authenticatedClientSetup = authenticatedClientFactory.create(port)
        sampleAppUserId = authenticatedClientSetup.appUserId
        authedClient = authenticatedClientSetup.client
    }

    @AfterEach
    fun clear() {
        authenticatedClientFactory.clear()
        emailSourceRepository.clearAllEventRules()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun runAITokenReport() = runBlocking {

        authedClient
            .post()
            .uri("/api/v1/reports/updates")
            .exchange()
            .expectStatus()
            .isAccepted()

        await.atMost(150, TimeUnit.SECONDS).pollInterval(5, TimeUnit.SECONDS).untilAsserted {
            transactionTemplate.execute {
                val appUser = appUserRepository.findByIdWithAccounts(sampleAppUserId)
                assert(appUser?.googleAccounts?.all { it.subscriptions.isNotEmpty() } == true)
            }
        }
    }
}

package com.matchalab.subscription_killer_api.perf

import com.matchalab.subscription_killer_api.config.AuthenticatedClientFactory
import com.matchalab.subscription_killer_api.config.SmallerSampleMessageConfig
import com.matchalab.subscription_killer_api.repository.AppUserRepository
import com.matchalab.subscription_killer_api.repository.EmailSourceRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.awaitility.kotlin.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.autoconfigure.observation.ObservationRegistryCustomizer
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

//@Tag("ai")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.main.allow-bean-definition-overriding=true",
        "spring.test.observability.auto-configure=true",
        "management.tracing.enabled=true",
        "management.zipkin.tracing.export.enabled=true",
        "management.zipkin.tracing.endpoint=http://localhost:9411/api/v2/spans",
        "management.tracing.sampling.probability=1.0"
    ]
)
//@EnableAutoConfiguration
@AutoConfigureWebTestClient
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
    private val observationRegistryProvider: ObjectProvider<ObservationRegistry>,
) {
    private lateinit var sampleAppUserId: UUID
    private lateinit var authedClient: WebTestClient
    private val observationRegistry: ObservationRegistry
        get() = observationRegistryProvider.getObject()

    @LocalServerPort
    private var port: Int = 0

    @BeforeEach
    fun setUp() {
        emailSourceRepository.clearAllEventRules()
        val authenticatedClientSetup = authenticatedClientFactory.create(port)
        sampleAppUserId = authenticatedClientSetup.appUserId
        authedClient = authenticatedClientSetup.client
    }

    @BeforeEach
    fun ensureTracingIsActive() {
        val registryClassName = observationRegistry::class.java.simpleName
        val registryFullClassName = observationRegistry::class.java.name

        println("=== DEBUGGING OBSERVATION REGISTRY ===")
        println("Registry class name: $registryClassName")
        println("Registry full class name: $registryFullClassName")

        // Check what type of ObservationRegistry we have
        if (observationRegistry is ObservationRegistryCustomizer<*>) {
            println("Registry is a customizer")
        }

        // Check if we have any observation handlers
        try {
            val field = observationRegistry::class.java.getDeclaredField("observationHandlers")
            field.isAccessible = true
            val handlers = field.get(observationRegistry) as Collection<*>
            println("Number of observation handlers: ${handlers.size}")
            handlers.forEach { handler ->
                println("Handler: ${handler?.javaClass?.name}")
            }
        } catch (e: Exception) {
            println("Could not access observation handlers: ${e.message}")
        }

        // Check Spring context for observation-related beans
        try {
            val context =
                org.springframework.test.context.TestContextManager(this::class.java).testContext.applicationContext
            println("=== SPRING BEANS ===")
            val observationBeanNames =
                context.getBeanNamesForType(org.springframework.context.ApplicationListener::class.java)
            println("Observation-related beans count: ${observationBeanNames.size}")
            observationBeanNames.forEach { beanName ->
                if (beanName.contains("observation") || beanName.contains("tracing") || beanName.contains("zipkin")) {
                    println("Found bean: $beanName")
                }
            }
        } catch (e: Exception) {
            println("Could not access Spring context: ${e.message}")
        }

        println("=== END DEBUGGING ===")

        assumeTrue(registryClassName != "SimpleObservationRegistry") {
            "Aborting test: ObservationRegistry is 'SimpleObservationRegistry'. " +
                    "Check Observation Configuration. Full class: $registryFullClassName"
        }
    }

    @AfterEach
    fun clear() {
        authenticatedClientFactory.clear()
        emailSourceRepository.clearAllEventRules()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun runAITokenReport() = runBlocking {
        val observation = Observation.createNotStarted("ai-token-report-test", observationRegistry)
            .contextualName("runAITokenReport")

        observation.observe {
            logger.info { "Starting AI token report test with observation" }
            logger.info { "Observation Registry: ${observationRegistry::class.simpleName}" }
            logger.info { "Current Observation: ${observationRegistry.currentObservation ?: "None"}" }

            authedClient
                .post()
                .uri("/api/v1/reports/updates")
                .exchange()
                .expectStatus()
                .isAccepted()

            logger.info { "Request sent, waiting for completion..." }

            await.atMost(150, TimeUnit.SECONDS).pollInterval(5, TimeUnit.SECONDS).untilAsserted {
                transactionTemplate.execute {
                    val appUser = appUserRepository.findByIdWithAccounts(sampleAppUserId)
                    assert(appUser?.googleAccounts?.all { it.subscriptions.isNotEmpty() } == true)
                }
            }

            logger.info { "Test completed, observation should be sent to Zipkin" }
        }
        logger.info { "Waiting for Zipkin export..." }
        delay(2000)
    }
}

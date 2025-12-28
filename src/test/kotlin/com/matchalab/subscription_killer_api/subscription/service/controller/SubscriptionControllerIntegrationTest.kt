package com.matchalab.subscription_killer_api.subscription.service.controller

import com.matchalab.subscription_killer_api.config.GoogleTestUserProperties
import com.matchalab.subscription_killer_api.config.TestDataFactory
import com.matchalab.subscription_killer_api.domain.AppUser
import com.matchalab.subscription_killer_api.domain.GoogleAccount
import com.matchalab.subscription_killer_api.domain.UserRoleType
import com.matchalab.subscription_killer_api.repository.AppUserRepository
import com.matchalab.subscription_killer_api.repository.ServiceProviderRepository
import com.matchalab.subscription_killer_api.subscription.dto.SubscriptionReportResponseDto
import com.matchalab.subscription_killer_api.subscription.service.ServiceProviderService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.transaction.annotation.Transactional
import java.util.*

private val logger = KotlinLogging.logger {}

//@Tag("ai")
//@Tag("gcp")
@SpringBootTest()
//@ActiveProfiles("dev", "gcp", "test")
@AutoConfigureMockMvc
@Transactional
@EnableConfigurationProperties(GoogleTestUserProperties::class)
class SubscriptionControllerIntegrationTest
@Autowired
constructor(
    val appUserRepository: AppUserRepository,
    val serviceProviderRepository: ServiceProviderRepository,
    val client: WebTestClient,
    @Autowired private val serviceProviderService: ServiceProviderService
) {
    @Autowired
    lateinit var googleTestUserProperties: GoogleTestUserProperties

    lateinit var customClient: WebTestClient

    private val testDataFactory = TestDataFactory(serviceProviderRepository)

    @BeforeEach
    fun setUp() {
        customClient =
            client.mutate().codecs { configurer -> configurer.defaultCodecs().enableLoggingRequestDetails(true) }
                .defaultHeader(HttpHeaders.ORIGIN, "https://localhost:3000")
                .baseUrl("/api/v1")
                .build()
    }

    @AfterEach
    fun tearDown() {
        appUserRepository.deleteAll()
    }

    @Test
    fun `when analyze subscription and then fetch should return the analyzed result for both requests`() {

        val testAppUser =
            AppUser(
                null,
                "testUserName",
                UserRoleType.USER,
                mutableListOf<GoogleAccount>()
            )
        testAppUser.addGoogleAccount(
            GoogleAccount(
                googleTestUserProperties.subject,
                "testUserName",
                "testUserEmail",
                googleTestUserProperties.refreshToken,
                googleTestUserProperties.accessToken,
                googleTestUserProperties.expiresAt,
                googleTestUserProperties.scope
            )
        )
        val testAppUserId: UUID = appUserRepository.save(testAppUser).id ?: UUID.randomUUID()

        // Given
        val principal = testAppUserId.toString()
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(
                principal,
                "{noop}",
                listOf(SimpleGrantedAuthority("ROLE_USER"))
            )
        customClient
            .get()
            .uri("/subscriptions/analysis")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody<SubscriptionReportResponseDto>()
            .consumeWith { response ->
                logger.debug { "response: $response" }
                val accountReports = response.responseBody?.accountReports ?: emptyList()
                assert(accountReports.isNotEmpty())

                accountReports.forEach { report ->
                    assert(report.subscriptions.isNotEmpty()) {
                        "Account ${report.googleAccount.name} has no subscriptions"
                    }
                }
            }

    }
}

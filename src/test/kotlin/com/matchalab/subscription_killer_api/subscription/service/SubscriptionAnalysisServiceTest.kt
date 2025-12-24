package com.matchalab.subscription_killer_api.subscription.service

import com.google.api.services.gmail.model.Message
import com.matchalab.subscription_killer_api.config.TestDataFactory
import com.matchalab.subscription_killer_api.domain.GoogleAccount
import com.matchalab.subscription_killer_api.gmail.MessageFetchPlan
import com.matchalab.subscription_killer_api.repository.GoogleAccountRepository
import com.matchalab.subscription_killer_api.repository.ServiceProviderRepository
import com.matchalab.subscription_killer_api.subscription.*
import com.matchalab.subscription_killer_api.subscription.dto.AccountReportDto
import com.matchalab.subscription_killer_api.subscription.dto.SubscriptionReportResponseDto
import com.matchalab.subscription_killer_api.subscription.dto.SubscriptionResponseDto
import com.matchalab.subscription_killer_api.utils.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.core.io.ClassPathResource
import java.time.Duration
import java.time.Instant
import java.util.*

private val logger = KotlinLogging.logger {}

@ExtendWith(MockitoExtension::class)
class SubscriptionAnalysisServiceTest(
) {
    private val dataFactory = TestDataFactory(mockk<ServiceProviderRepository>())

    private val googleAccountRepository = mockk<GoogleAccountRepository>()

    private val serviceProviderService = mockk<ServiceProviderService>()

    private val multiAccountGmailAggregationService = mockk<MultiAccountGmailAggregationService>()

    private val gmailClientFactory = mockk<GmailClientFactory>()

    private val mockGmailClientAdapter = mockk<GmailClientAdapter>()

    private val testMailProperties = MailProperties(analysisMonths = 13L)

    private val subscriptionAnalysisService = SubscriptionAnalysisService(
        googleAccountRepository,
        multiAccountGmailAggregationService,
        serviceProviderService,
        gmailClientFactory,
        testMailProperties
    )

    private val mockNetflixServiceProvider: ServiceProvider =
        dataFactory.createServiceProvider(
            "Netflix",
            mutableListOf(
                EmailSource(
                    null, "info@account.netflix.com", mutableMapOf(
                        SubscriptionEventType.PAID_SUBSCRIPTION_START to EmailDetectionRule(
                            SubscriptionEventType.PAID_SUBSCRIPTION_START,
                            listOf("계정 정보 변경 확인"),
                            snippetKeywords = listOf("새로운 결제 수단")
                        ),
                        SubscriptionEventType.PAID_SUBSCRIPTION_CANCEL to EmailDetectionRule(
                            SubscriptionEventType.PAID_SUBSCRIPTION_CANCEL,
                            subjectRegex = "멤버십을 다시 시작하세요",
                            snippetRegex = "멤버십이 보류 중",
                        )
                    )
                )
            ),
        )

    private val mockSketchfabServiceProvider: ServiceProvider =
        dataFactory.createServiceProvider(
            "Sketchfab",
            mutableListOf(EmailSource(null, "notifications@sketchfab.com")),
        )

    val fakeGoogleAccountA: GoogleAccount = GoogleAccount("FAKE_SUBJECT_A", "FAKE_NAME_A")
    val fakeGoogleAccountB: GoogleAccount = GoogleAccount("FAKE_SUBJECT_B", "FAKE_NAME_B")

    lateinit var fakeGmailMessages: List<GmailMessage>

    private val mockServiceProviders = listOf(mockNetflixServiceProvider, mockSketchfabServiceProvider)

    @BeforeEach
    fun setUp() {

        val jsonPath = "mocks/messages_netflix_sketchfab.json"
        val rawMessages: List<Message> = readMessages(ClassPathResource(jsonPath).inputStream)
        fakeGmailMessages = rawMessages.mapNotNull { it.toGmailMessage() }
    }

    @Test
    fun `given GmailClientFactory should correctly create subscription report response dto`() =
        runTest {
            val expectedSubscriptions: List<SubscriptionResponseDto> =
                listOf(
                    SubscriptionResponseDto(
                        serviceProvider = mockNetflixServiceProvider.toDto(),
                        hasSubscribedNewsletterOrAd = false,
                        paidSince =
                            DateTimeUtils.epochMilliToInstant(1752702283000),
                        registeredSince = null,
                        isNotSureIfPaymentIsOngoing = false,
                    ),
                    SubscriptionResponseDto(
                        serviceProvider = mockSketchfabServiceProvider.toDto(),
                        hasSubscribedNewsletterOrAd = false,
                        paidSince = null,
                        registeredSince = null,
                        isNotSureIfPaymentIsOngoing = false,
                    )
                )

            val expectedSubscriptionReportResponseDto: SubscriptionReportResponseDto =
                SubscriptionReportResponseDto(
                    listOf(
                        AccountReportDto(
                            expectedSubscriptions,
                            fakeGoogleAccountA.toResponseDto(),
                            null
                        ),
                        AccountReportDto(
                            expectedSubscriptions,
                            fakeGoogleAccountB.toResponseDto(),
                            null
                        )
                    )
                )

            every {
                googleAccountRepository.findById(fakeGoogleAccountA.subject!!)
            } answers { Optional.of(fakeGoogleAccountA) }
            every {
                googleAccountRepository.findById(fakeGoogleAccountB.subject!!)
            } answers { Optional.of(fakeGoogleAccountB) }

            every { googleAccountRepository.save(any<GoogleAccount>()) } answers {
                firstArg<GoogleAccount>()
            }

            every {
                multiAccountGmailAggregationService.getGoogleAccountSubjects()
            } answers {
                listOfNotNull(fakeGoogleAccountA.subject, fakeGoogleAccountB.subject)
            }

            every {
                serviceProviderService.findAll()
            } returns
                    mockServiceProviders

            every {
                serviceProviderService.findByActiveEmailAddressesIn(any())
            } returns
                    mockServiceProviders

            every {
                serviceProviderService.addEmailSourcesFromMessages(any())
            } returns
                    mockServiceProviders


            every {
                serviceProviderService.updateEmailDetectionRules(
                    any<ServiceProvider>(),
                    any<Map<String, List<GmailMessage>>>()
                )
            } answers {
                firstArg()
            }

            every { gmailClientFactory.createAdapter(any<String>()) } returns (mockGmailClientAdapter)

            coEvery { mockGmailClientAdapter.listMessageIds(any<String>()) } returns (fakeGmailMessages.map { it.id })

            coEvery { mockGmailClientAdapter.getMessages(any<List<String>>(), any<MessageFetchPlan>()) } answers {
                fakeGmailMessages
            }

            // When
            val result: SubscriptionReportResponseDto = subscriptionAnalysisService.analyze()

            // Then
            logger.debug { "[subscriptionAnalysisService.analyze] result: $result" }

            assertThat(result)
                .isNotNull()
                .usingRecursiveComparison()
                .ignoringFields("accountReports.analyzedAt")
                .isEqualTo(expectedSubscriptionReportResponseDto)

            assertThat(result.accountReports).allSatisfy { accountReport ->
                assertThat(accountReport.analyzedAt)
                    .isBetween(Instant.now().minus(Duration.ofMinutes(10)), Instant.now())
            }
        }
}
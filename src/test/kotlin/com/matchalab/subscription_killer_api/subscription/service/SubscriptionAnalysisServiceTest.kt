package com.matchalab.subscription_killer_api.subscription.service

import com.google.api.services.gmail.model.Message
import com.matchalab.subscription_killer_api.config.TestDataFactory
import com.matchalab.subscription_killer_api.domain.GoogleAccount
import com.matchalab.subscription_killer_api.gmail.MessageFetchPlan
import com.matchalab.subscription_killer_api.repository.GoogleAccountRepository
import com.matchalab.subscription_killer_api.service.AppUserService
import com.matchalab.subscription_killer_api.subscription.*
import com.matchalab.subscription_killer_api.subscription.dto.SubscriptionResponseDto
import com.matchalab.subscription_killer_api.subscription.progress.service.ProgressService
import com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter.GmailClientAdapter
import com.matchalab.subscription_killer_api.subscription.service.gmailclientfactory.GmailClientFactory
import com.matchalab.subscription_killer_api.utils.DateTimeUtils
import com.matchalab.subscription_killer_api.utils.toDto
import com.matchalab.subscription_killer_api.utils.toGmailMessage
import com.matchalab.subscription_killer_api.utils.toResponseDto
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.observation.ObservationRegistry
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Duration
import java.time.Instant
import java.util.*


private val logger = KotlinLogging.logger {}

@ExtendWith(MockitoExtension::class)
class SubscriptionAnalysisServiceTest(
) {
    private val dataFactory = TestDataFactory()

    private val googleAccountRepository = mockk<GoogleAccountRepository>()

    private val serviceProviderService = mockk<ServiceProviderService>()

    private val appUserService = mockk<AppUserService>()

    private val gmailClientFactory = mockk<GmailClientFactory>()

    private val mockGmailClientAdapter = mockk<GmailClientAdapter>()

    private val progressService = mockk<ProgressService>(relaxUnitFun = true)

    private val testMailProperties = MailProperties(analysisMonths = 13L)

    private lateinit var subscriptionAnalysisService: SubscriptionAnalysisService

    private val testAppUserId: UUID = UUID.randomUUID()

    private val mockNetflixServiceProvider: ServiceProvider =
        dataFactory.createServiceProvider(
            "Netflix",
            mutableListOf(
                EmailSource(
                    null, "info@account.netflix.com", mutableListOf(
                        EmailDetectionRule(
                            true, Instant.now(),
                            SubscriptionEventType.SUBSCRIPTION_START,
                            EmailTemplate(
                                "계정 정보 변경 확인",
                                "새로운 결제 수단"
                            )
                        ),
                        EmailDetectionRule(
                            true, Instant.now(),
                            SubscriptionEventType.SUBSCRIPTION_CANCEL,
                            EmailTemplate(
                                "멤버십을 다시 시작하세요",
                                "멤버십이 보류 중",
                            )
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

    val fakeGoogleAccountA: GoogleAccount = GoogleAccount("FAKE_SUBJECT_A", "FAKE_NAME_A", "FAKE_EMAIL_A")
    val fakeGoogleAccountB: GoogleAccount = GoogleAccount("FAKE_SUBJECT_B", "FAKE_NAME_B", "FAKE_EMAIL_B")

    lateinit var fakeGmailMessages: List<GmailMessage>

    private val mockServiceProviders = listOf(mockNetflixServiceProvider, mockSketchfabServiceProvider)

    @BeforeEach
    fun setUp() {

        val rawMessages: List<Message> = dataFactory.loadSampleMessages()
        fakeGmailMessages = rawMessages.mapNotNull { it.toGmailMessage() }

        subscriptionAnalysisService = SubscriptionAnalysisService(
            googleAccountRepository,
            appUserService,
            serviceProviderService,
            gmailClientFactory,
            testMailProperties,
            progressService,
            ObservationRegistry.NOOP,
        )
    }

    @Test
    fun `given GmailClientFactory should correctly create subscription report response dto`() =
        runTest {
            val expectedSubscriptions: List<SubscriptionResponseDto> =
                listOf(
                    SubscriptionResponseDto(
                        serviceProvider = mockNetflixServiceProvider.toDto(),
                        hasSubscribedNewsletterOrAd = false,
                        subscribedSince =
                            DateTimeUtils.epochMilliToInstant(1752702283000),
                        registeredSince = null,
                        isNotSureIfPaymentIsOngoing = false,
                    ),
                    SubscriptionResponseDto(
                        serviceProvider = mockSketchfabServiceProvider.toDto(),
                        hasSubscribedNewsletterOrAd = false,
                        subscribedSince = null,
                        registeredSince = null,
                        isNotSureIfPaymentIsOngoing = false,
                    )
                )

            every {
                googleAccountRepository.findByIdWithSubscriptionsAndProviders(fakeGoogleAccountA.subject!!)
            } answers { fakeGoogleAccountA }
            every {
                googleAccountRepository.findByIdWithSubscriptionsAndProviders(fakeGoogleAccountB.subject!!)
            } answers { fakeGoogleAccountB }

            every { googleAccountRepository.save(any<GoogleAccount>()) } answers {
                firstArg<GoogleAccount>()
            }

            every {
                appUserService.findGoogleAccountSubjectsByAppUserId(any<UUID>())
            } answers {
                listOfNotNull(fakeGoogleAccountA.subject, fakeGoogleAccountB.subject)
            }

            every {
                serviceProviderService.findByIdWithSubscriptions(mockNetflixServiceProvider.requiredId)
            } returns
                    mockNetflixServiceProvider

            every {
                serviceProviderService.findByIdWithSubscriptions(mockSketchfabServiceProvider.requiredId)
            } returns
                    mockSketchfabServiceProvider

            every {
                serviceProviderService.findAllWithEmailSourcesAndAliases()
            } returns
                    mockServiceProviders

            every {
                serviceProviderService.findByActiveEmailAddressesInWithEmailSources(any())
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

            coEvery {
                mockGmailClientAdapter.getMessages(
                    any<List<String>>(),
                    MessageFetchPlan.INTERNAL_DATE_SNIPPET_FROM_SUBJECT
                )
            } answers {
                fakeGmailMessages
            }

            coEvery {
                mockGmailClientAdapter.getMessages(
                    any<List<String>>(),
                    MessageFetchPlan.INTERNAL_DATE
                )
            } answers {
                listOf(
                    fakeGmailMessages.filter { it.senderEmail == mockNetflixServiceProvider.emailSearchAddresses.first() }
                        .minBy { it.internalDate },
                    fakeGmailMessages.filter { it.senderEmail == mockSketchfabServiceProvider.emailSearchAddresses.first() }
                        .minBy { it.internalDate })
            }

            coEvery { mockGmailClientAdapter.getFirstMessageId(any<List<String>>()) } returns ("fakeFirstMessageId")

            coEvery { mockGmailClientAdapter.getFirstMessageId(any<List<String>>()) } returns ("fakeFirstMessageId")

            // When
            subscriptionAnalysisService.analyze(testAppUserId)

            val capturedAccounts = mutableListOf<GoogleAccount>()

            verify(exactly = 2) {
                googleAccountRepository.save(capture(capturedAccounts))
            }

            // Then
            logger.debug { "[subscriptionAnalysisService.analyze] capturedAccounts: $capturedAccounts" }

            assertThat(capturedAccounts).allSatisfy { capturedAccount ->
                assertThat(capturedAccount.subscriptions.map { it.toResponseDto() }).isNotEmpty()
                    .usingRecursiveComparison()
                    .isEqualTo(expectedSubscriptions)
                assertThat(capturedAccount.analyzedAt)
                    .isBetween(Instant.now().minus(Duration.ofMinutes(10)), Instant.now())
            }
        }
}
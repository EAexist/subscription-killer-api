package com.matchalab.subscription_killer_api.subscription.service

import com.matchalab.subscription_killer_api.config.TestDataFactory
import com.matchalab.subscription_killer_api.domain.GoogleAccount
import com.matchalab.subscription_killer_api.gmail.MessageFetchPlan
import com.matchalab.subscription_killer_api.repository.GoogleAccountRepository
import com.matchalab.subscription_killer_api.service.AppUserService
import com.matchalab.subscription_killer_api.subscription.*
import com.matchalab.subscription_killer_api.subscription.config.MailProperties
import com.matchalab.subscription_killer_api.subscription.progress.service.ProgressService
import com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter.GmailClientAdapter
import com.matchalab.subscription_killer_api.subscription.service.gmailclientfactory.GmailClientFactory
import com.matchalab.subscription_killer_api.utils.DateTimeUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.observation.ObservationRegistry
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.persistence.EntityNotFoundException
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
    private val subscriptionService = mockk<SubscriptionService>()

    private val appUserService = mockk<AppUserService>()

    private val gmailClientFactory = mockk<GmailClientFactory>()

    private val mockGmailClientAdapter = mockk<GmailClientAdapter>()

    private val progressService = mockk<ProgressService>(relaxUnitFun = true)

    private val testMailProperties = MailProperties(analysisMonths = 13L, maxSnippetSize = 150)

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

    private val fakeGmailMessages: List<GmailMessage> = dataFactory.loadSampleMessages()

    private val mockServiceProviders = listOf(mockNetflixServiceProvider, mockSketchfabServiceProvider)

    @BeforeEach
    fun setUp() {

        subscriptionAnalysisService = SubscriptionAnalysisService(
            googleAccountRepository,
            appUserService,
            serviceProviderService,
            gmailClientFactory,
            testMailProperties,
            progressService,
            ObservationRegistry.NOOP,
            subscriptionService
        )
    }

    @Test
    fun `given GmailClientFactory should correctly create subscription report response dto`() =
        runTest {
            val expectedSubscriptions: List<Subscription> =
                listOf(
                    Subscription(
                        serviceProvider = mockNetflixServiceProvider,
                        hasSubscribedNewsletterOrAd = false,
                        subscribedSince =
                            DateTimeUtils.epochMilliToInstant(1752702283000),
                        registeredSince = null,
                        isNotSureIfSubscriptionIsOngoing = false,
                    ),
                    Subscription(
                        serviceProvider = mockSketchfabServiceProvider,
                        hasSubscribedNewsletterOrAd = false,
                        subscribedSince = null,
                        registeredSince = null,
                        isNotSureIfSubscriptionIsOngoing = false,
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

            every {
                subscriptionService.findByGoogleAccountAndServiceProviderIdOrCreate(
                    any<GoogleAccount>(),
                    any<UUID>()
                )
            } answers {
                Subscription(
                    serviceProvider = serviceProviderService.findByIdWithSubscriptions(secondArg())
                        ?: throw EntityNotFoundException()
                )
            }

            every {
                subscriptionService.save(any<Subscription>())
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
            val capturedSubscriptions = mutableListOf<Subscription>()

            verify(exactly = 2) {
                googleAccountRepository.save(capture(capturedAccounts))
            }

            verify(exactly = 4) {
                subscriptionService.save(capture(capturedSubscriptions))
            }

            // Then
            logger.debug { "[subscriptionAnalysisService.analyze] capturedAccounts: $capturedAccounts" }
            logger.debug { "[subscriptionAnalysisService.analyze] capturedSubscriptions: $capturedSubscriptions" }

            assertThat(capturedSubscriptions)
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyInAnyOrder(*(expectedSubscriptions + expectedSubscriptions).toTypedArray())

            assertThat(capturedAccounts).allSatisfy { capturedAccount ->
                assertThat(capturedAccount.analyzedAt)
                    .isBetween(Instant.now().minus(Duration.ofMinutes(10)), Instant.now())
            }
        }
}
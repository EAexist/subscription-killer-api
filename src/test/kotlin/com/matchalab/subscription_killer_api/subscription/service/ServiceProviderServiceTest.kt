package com.matchalab.subscription_killer_api.subscription.service

import com.matchalab.subscription_killer_api.config.TestDataFactory
import com.matchalab.subscription_killer_api.repository.EmailSourceRepository
import com.matchalab.subscription_killer_api.repository.ServiceProviderRepository
import com.matchalab.subscription_killer_api.repository.SubscriptionRepository
import com.matchalab.subscription_killer_api.subscription.EmailDetectionRule
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.subscription.ServiceProvider
import com.matchalab.subscription_killer_api.subscription.SubscriptionEventType
import com.matchalab.subscription_killer_api.utils.readMessages
import com.matchalab.subscription_killer_api.utils.toGmailMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.core.io.ClassPathResource
import java.util.*

private val logger = KotlinLogging.logger {}

@ExtendWith(MockitoExtension::class)
class ServiceProviderServiceTest() {

    private val mockServiceProviderRepository = mockk<ServiceProviderRepository>()
    private val mockSubscriptionRepository = mockk<SubscriptionRepository>()
    private val mockEmailSourceRepository = mockk<EmailSourceRepository>()
    private val mockEmailDetectionRuleService = mockk<EmailDetectionRuleService>()

    private val dataFactory = TestDataFactory(mockk<ServiceProviderRepository>())

    private val serviceProviderService = ServiceProviderService(
        mockServiceProviderRepository,
        mockSubscriptionRepository,
        mockEmailSourceRepository,
        mockEmailDetectionRuleService
    )

    val maxNumberOfEmailDetectionRuleAnalysis: Long = 40

    private val netflixDisplayName = "Netflix"
    private val sketchfabDisplayName = "Sketchfab"
    private val netflixEmailAddress = "info@account.netflix.com"
    private val sketchfabEmailAddress = "notifications@sketchfab.com"

    private val mockNetflixEmailDetectionRule_PAID_SUBSCRIPTION_START: EmailDetectionRule =
        EmailDetectionRule(
            SubscriptionEventType.PAID_SUBSCRIPTION_START,
            listOf("계정 정보 변경 확인"),
            snippetKeywords = listOf("새로운 결제 수단 정보가 등록")
        )

    private val mockNetflixEmailDetectionRule_PAID_SUBSCRIPTION_CANCEL: EmailDetectionRule = EmailDetectionRule(
        SubscriptionEventType.PAID_SUBSCRIPTION_CANCEL,
        listOf("멤버십이 보류 중입니다")
    )

    private val mockSketchfabEmailDetectionRule_MONTHLY_PAYMENT: EmailDetectionRule = EmailDetectionRule(
        SubscriptionEventType.MONTHLY_PAYMENT,
        listOf("결제 완료")
    )

    private val fakeGmailMessages: List<GmailMessage> by lazy {
        val jsonPath = "static/messages_netflix_sketchfab.json"
        readMessages(ClassPathResource(jsonPath).inputStream).mapNotNull { it.toGmailMessage() }
    }

    private val fakeAddressToGmailMessages: Map<String, List<GmailMessage>> =
        fakeGmailMessages.groupBy { it.senderEmail }

    @BeforeEach
    fun setUp() {

        // Given
        every {
            mockServiceProviderRepository.findAll()
        } returns emptyList<ServiceProvider>()

        every {
            mockServiceProviderRepository.save(any<ServiceProvider>())
        } answers {
            val provider = firstArg<ServiceProvider>()
            ServiceProvider(
                provider.id ?: UUID.randomUUID(),
                provider.displayName,
                provider.aliasNames,
                provider.emailSources,
                provider.paymentCycle,
            )
        }

        every {
            mockServiceProviderRepository.saveAll(any<List<ServiceProvider>>())
        } answers {
            val providers = firstArg<List<ServiceProvider>>()
            providers.map {
                ServiceProvider(
                    it.id ?: UUID.randomUUID(),
                    it.displayName,
                    it.aliasNames,
                    it.emailSources,
                    it.paymentCycle,
                )

            }
        }
    }

    @Test
    fun `given new email addresses when addEmailSourcesFromMessages then add emailSources`() =
        runTest {

            // Given
            every {
                mockEmailSourceRepository.findExistingAddresses(any<List<String>>())
            } answers {
                emptySet()
            }

            every {
                mockServiceProviderRepository.findByAliasName(netflixDisplayName)
            } returns
                    dataFactory.createServiceProvider(
                        netflixDisplayName, null
                    )

            every {
                mockServiceProviderRepository.findByAliasName(sketchfabDisplayName)
            } returns
                    dataFactory.createServiceProvider(
                        sketchfabDisplayName, null
                    )

            // When
            val updatedServiceProviders: List<ServiceProvider> = serviceProviderService.addEmailSourcesFromMessages(
                fakeGmailMessages
            )

            //Then
            val expectedServiceProviders = listOf(
                dataFactory.createServiceProvider(
                    netflixDisplayName,
                    mutableListOf(dataFactory.createEmailSource(netflixEmailAddress, null))
                ),
                dataFactory.createServiceProvider(
                    sketchfabDisplayName,
                    mutableListOf(dataFactory.createEmailSource(sketchfabEmailAddress, null))
                ),
            )

            assertThat(updatedServiceProviders)
                .isNotNull().allSatisfy { provider ->
                    assertThat(provider.id).isNotNull()
                    assertThat(provider.emailSources)
                        .isNotEmpty()
                }
                .usingRecursiveComparison()
                .ignoringFields(
                    "id",
                    "emailSources.id",
                    "emailSources.serviceProvider"
                )
                .isEqualTo(expectedServiceProviders)
        }

    @Test
    fun `given incomplete rules and remaining chatclient quota then update via chatclient`() =
        runTest {
            every {
                mockEmailDetectionRuleService.updateRules(
                    any(),
                    any()
                )
            } returns mapOf<SubscriptionEventType, EmailDetectionRule>(
                SubscriptionEventType.PAID_SUBSCRIPTION_START to mockNetflixEmailDetectionRule_PAID_SUBSCRIPTION_START,
                SubscriptionEventType.PAID_SUBSCRIPTION_CANCEL to mockNetflixEmailDetectionRule_PAID_SUBSCRIPTION_CANCEL
            )


            every {

                mockSubscriptionRepository.countByServiceProviderId(any<UUID>())
            } returns maxNumberOfEmailDetectionRuleAnalysis - 1

            for (serviceProvider in listOf(
                dataFactory.createServiceProvider(
                    netflixDisplayName, mutableListOf(dataFactory.createEmailSource(netflixEmailAddress, null))
                ),
                dataFactory.createServiceProvider(
                    netflixDisplayName, mutableListOf(
                        dataFactory.createEmailSource(
                            netflixEmailAddress, mutableMapOf(
                                SubscriptionEventType.PAID_SUBSCRIPTION_START to mockNetflixEmailDetectionRule_PAID_SUBSCRIPTION_START
                            )
                        )
                    )
                )
            )
            ) {
                // When
                val updatedServiceProvider: ServiceProvider =
                    serviceProviderService.updateEmailDetectionRules(
                        serviceProvider,
                        fakeAddressToGmailMessages
                    )

                // Then
                val expectedServiceProvider =
                    dataFactory.createServiceProvider(
                        netflixDisplayName, mutableListOf(
                            dataFactory.createEmailSource(
                                netflixEmailAddress, mutableMapOf(
                                    SubscriptionEventType.PAID_SUBSCRIPTION_START to mockNetflixEmailDetectionRule_PAID_SUBSCRIPTION_START,
                                    SubscriptionEventType.PAID_SUBSCRIPTION_CANCEL to mockNetflixEmailDetectionRule_PAID_SUBSCRIPTION_CANCEL
                                )
                            )
                        )
                    )

                verify(atLeast = 1) {
                    mockServiceProviderRepository.save(any<ServiceProvider>())
                }
                assertThat(updatedServiceProvider).isNotNull()
                    .usingRecursiveComparison()
                    .ignoringFields("id", "serviceProvider")
                    .isEqualTo(expectedServiceProvider)
                assertThat(updatedServiceProvider.id).isNotNull()
                assertThat(updatedServiceProvider.emailSources)
            }

        }

    @Test
    fun `given PAID_SUBSCRIPTION_START and PAID_SUBSCRIPTION_CANCEL eventRule when updateEmailDetectionRules then skip EmailDetectionRuleService call`() =
        runTest {

            // Given
            val serviceProvider: ServiceProvider =
                dataFactory.createServiceProvider(
                    netflixDisplayName, mutableListOf(
                        dataFactory.createEmailSource(
                            netflixEmailAddress, mutableMapOf(
                                SubscriptionEventType.PAID_SUBSCRIPTION_START to mockNetflixEmailDetectionRule_PAID_SUBSCRIPTION_START,
                            )
                        ),
                        dataFactory.createEmailSource(
                            netflixEmailAddress, mutableMapOf(
                                SubscriptionEventType.PAID_SUBSCRIPTION_CANCEL to mockNetflixEmailDetectionRule_PAID_SUBSCRIPTION_CANCEL,
                            )
                        )

                    )
                )
            every {
                mockSubscriptionRepository.countByServiceProviderId(any<UUID>())
            } returns maxNumberOfEmailDetectionRuleAnalysis - 1

            // When
            val updatedServiceProvider: ServiceProvider =
                serviceProviderService.updateEmailDetectionRules(serviceProvider, fakeAddressToGmailMessages)

            // Then
            verify(exactly = 0) {
                mockEmailDetectionRuleService.updateRules(
                    any(),
                    any()
                )
            }

            assertThat(updatedServiceProvider).isNotNull()
                .usingRecursiveComparison()
                .ignoringFields("id")
                .isEqualTo(serviceProvider)
            assertThat(updatedServiceProvider.id).isNotNull()
        }


    @Test
    fun `given MONTHLY_PAYMENT eventRule when updateEmailDetectionRules then skip EmailDetectionRuleService call`() =
        runTest {

            // Given
            val serviceProvider: ServiceProvider =
                dataFactory.createServiceProvider(
                    sketchfabDisplayName, mutableListOf(
                        dataFactory.createEmailSource(
                            sketchfabEmailAddress, mutableMapOf(
                                SubscriptionEventType.MONTHLY_PAYMENT to mockSketchfabEmailDetectionRule_MONTHLY_PAYMENT,
                            )
                        )
                    )
                )

            every {
                mockSubscriptionRepository.countByServiceProviderId(any<UUID>())
            } returns maxNumberOfEmailDetectionRuleAnalysis - 1

            // When
            val updatedServiceProvider: ServiceProvider =
                serviceProviderService.updateEmailDetectionRules(serviceProvider, fakeAddressToGmailMessages)

            // Then
            verify(exactly = 0) {
                mockEmailDetectionRuleService.updateRules(
                    any(),
                    any()
                )
            }

            assertThat(updatedServiceProvider).isNotNull()
                .usingRecursiveComparison()
                .ignoringFields("id")
                .isEqualTo(serviceProvider)
            assertThat(updatedServiceProvider.id).isNotNull()

        }

    @Test
    fun `given incomplete rules and exhausted chatclient quota then skip EmailDetectionRuleService call`() =
        runTest {

            // Given
            every {
                mockSubscriptionRepository.countByServiceProviderId(any<UUID>())
            } returns maxNumberOfEmailDetectionRuleAnalysis

            for (serviceProvider in listOf(
                dataFactory.createServiceProvider(
                    netflixDisplayName,
                    mutableListOf(dataFactory.createEmailSource(netflixEmailAddress, null))
                ),
                dataFactory.createServiceProvider(
                    netflixDisplayName, mutableListOf(
                        dataFactory.createEmailSource(
                            netflixEmailAddress, mutableMapOf(
                                SubscriptionEventType.PAID_SUBSCRIPTION_START to mockNetflixEmailDetectionRule_PAID_SUBSCRIPTION_START
                            )
                        )
                    )
                )
            )) {

                // When
                val updatedServiceProvider: ServiceProvider =
                    serviceProviderService.updateEmailDetectionRules(serviceProvider, fakeAddressToGmailMessages)

                // Then
                verify(exactly = 0) {
                    mockEmailDetectionRuleService.updateRules(
                        any(),
                        any()
                    )
                }

                assertThat(updatedServiceProvider).isNotNull()
                    .usingRecursiveComparison()
                    .ignoringFields("id")
                    .isEqualTo(serviceProvider)
                assertThat(updatedServiceProvider.id).isNotNull()
            }
        }
}
package com.matchalab.subscription_killer_api.subscription.service

import com.matchalab.subscription_killer_api.config.TestDataFactory
import com.matchalab.subscription_killer_api.repository.EmailSourceRepository
import com.matchalab.subscription_killer_api.repository.ServiceProviderRepository
import com.matchalab.subscription_killer_api.repository.SubscriptionRepository
import com.matchalab.subscription_killer_api.subscription.*
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
import java.time.Duration
import java.time.Instant
import java.util.*

private val logger = KotlinLogging.logger {}

@ExtendWith(MockitoExtension::class)
class ServiceProviderServiceTest() {

    private val mockServiceProviderRepository = mockk<ServiceProviderRepository>()
    private val mockSubscriptionRepository = mockk<SubscriptionRepository>()
    private val mockEmailSourceRepository = mockk<EmailSourceRepository>()
    private val mockEmailDetectionRuleService = mockk<EmailDetectionRuleService>()

    private val dataFactory = TestDataFactory()

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

    private val mockNetflixEmailDetectionRuleGenerationDto_SUBSCRIPTION_START: EmailDetectionRuleGenerationDto =
        EmailDetectionRuleGenerationDto(
            SubscriptionEventType.SUBSCRIPTION_START,
            EmailTemplate(
                "계정 정보 변경 확인",
                "새로운 결제 수단 정보가 등록"
            )
        )

    private val mockNetflixEmailDetectionRuleGenerationDto_SUBSCRIPTION_CANCEL: EmailDetectionRuleGenerationDto =
        EmailDetectionRuleGenerationDto(
            SubscriptionEventType.SUBSCRIPTION_CANCEL,
            EmailTemplate(
                "멤버십이 보류 중입니다",
                ".*"
            )
        )

    private val mockSketchfabEmailDetectionRuleGenerationDto_MONTHLY_PAYMENT: EmailDetectionRuleGenerationDto =
        EmailDetectionRuleGenerationDto(
            SubscriptionEventType.MONTHLY_PAYMENT,
            EmailTemplate(
                "결제 완료",
                ".*"
            )
        )

    private val fakeGmailMessages: List<GmailMessage> by lazy {
        dataFactory.loadSampleMessages()
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
                provider.logoDevSuffix,
                provider.websiteUrl,
                provider.subscriptionPageUrl,
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
                    it.logoDevSuffix,
                    it.websiteUrl,
                    it.subscriptionPageUrl,
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
                mockServiceProviderRepository.findByAliasNameWithEmailSources(netflixDisplayName)
            } returns
                    dataFactory.createServiceProvider(
                        netflixDisplayName, null
                    )

            every {
                mockServiceProviderRepository.findByAliasNameWithEmailSources(sketchfabDisplayName)
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
                    mutableListOf(dataFactory.createEmailSource(netflixEmailAddress))
                ),
                dataFactory.createServiceProvider(
                    sketchfabDisplayName,
                    mutableListOf(dataFactory.createEmailSource(sketchfabEmailAddress))
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
                mockEmailDetectionRuleService.generateRules(
                    any(),
                    any()
                )
            } returns mapOf<SubscriptionEventType, EmailDetectionRuleGenerationDto>(
                SubscriptionEventType.SUBSCRIPTION_START to mockNetflixEmailDetectionRuleGenerationDto_SUBSCRIPTION_START,
                SubscriptionEventType.SUBSCRIPTION_CANCEL to mockNetflixEmailDetectionRuleGenerationDto_SUBSCRIPTION_CANCEL
            )


            every {

                mockSubscriptionRepository.countByServiceProviderId(any<UUID>())
            } returns maxNumberOfEmailDetectionRuleAnalysis - 1

            val testUpdateAt = Instant.now()

            for (serviceProvider in listOf(
                dataFactory.createServiceProvider(
                    netflixDisplayName,
                    mutableListOf(dataFactory.createEmailSource(netflixEmailAddress))
                ),
                dataFactory.createServiceProvider(
                    netflixDisplayName, mutableListOf(
                        dataFactory.createEmailSource(
                            netflixEmailAddress, mutableListOf(
                                EmailDetectionRule.createActive(
                                    mockNetflixEmailDetectionRuleGenerationDto_SUBSCRIPTION_START, testUpdateAt
                                )
                            )
                        )
                    )
                )
            )
            ) {
                val expectedServiceProvider =
                    dataFactory.createServiceProvider(
                        netflixDisplayName, mutableListOf(
                            dataFactory.createEmailSource(
                                netflixEmailAddress, (serviceProvider.emailSources[0].eventRules + mutableListOf(
                                    EmailDetectionRule.createActive(
                                        mockNetflixEmailDetectionRuleGenerationDto_SUBSCRIPTION_START,
                                        testUpdateAt
                                    ),
                                    EmailDetectionRule.createActive(
                                        mockNetflixEmailDetectionRuleGenerationDto_SUBSCRIPTION_CANCEL,
                                        testUpdateAt
                                    )
                                )).toMutableList(),
                                fakeGmailMessages.map { it.id }.toMutableSet()
                            )
                        )
                    )

                // When
                val updatedServiceProvider: ServiceProvider =
                    serviceProviderService.updateEmailDetectionRules(
                        serviceProvider,
                        fakeAddressToGmailMessages
                    )

                // Then
                verify(atLeast = 1) {
                    mockServiceProviderRepository.save(any<ServiceProvider>())
                }
                assertThat(updatedServiceProvider).isNotNull()
                    .usingRecursiveComparison()
                    .ignoringFields("id", "serviceProvider", "emailSources.eventRules.updatedAt")
                    .isEqualTo(expectedServiceProvider)
                assertThat(updatedServiceProvider.id).isNotNull()
                assertThat(updatedServiceProvider.emailSources).allSatisfy { source ->
                    assertThat(source.eventRules).allSatisfy { rule ->
                        assertThat(rule.updatedAt)
                            .isAfterOrEqualTo(testUpdateAt)
                            .isBeforeOrEqualTo(testUpdateAt.plus(Duration.ofMinutes(1)))
                    }
                }
            }

        }

    @Test
    fun `given SUBSCRIPTION_START and SUBSCRIPTION_CANCEL eventRule when updateEmailDetectionRules then skip EmailDetectionRuleService call`() =
        runTest {

            // Given
            val serviceProvider: ServiceProvider =
                dataFactory.createServiceProvider(
                    netflixDisplayName, mutableListOf(
                        dataFactory.createEmailSource(
                            netflixEmailAddress, mutableListOf(
                                EmailDetectionRule.createActive(
                                    mockNetflixEmailDetectionRuleGenerationDto_SUBSCRIPTION_START,
                                    Instant.now()
                                ),
                            )
                        ),
                        dataFactory.createEmailSource(
                            netflixEmailAddress, mutableListOf(
                                EmailDetectionRule.createActive(
                                    mockNetflixEmailDetectionRuleGenerationDto_SUBSCRIPTION_CANCEL,
                                    Instant.now()
                                ),
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
                mockEmailDetectionRuleService.generateRules(
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
                            sketchfabEmailAddress, mutableListOf(
                                EmailDetectionRule.createActive(
                                    mockSketchfabEmailDetectionRuleGenerationDto_MONTHLY_PAYMENT,
                                    Instant.now()
                                ),
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
                mockEmailDetectionRuleService.generateRules(
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
                    mutableListOf(dataFactory.createEmailSource(netflixEmailAddress))
                ),
                dataFactory.createServiceProvider(
                    netflixDisplayName, mutableListOf(
                        dataFactory.createEmailSource(
                            netflixEmailAddress, mutableListOf(
                                EmailDetectionRule.createActive(
                                    mockNetflixEmailDetectionRuleGenerationDto_SUBSCRIPTION_START,
                                    Instant.now()
                                )
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
                    mockEmailDetectionRuleService.generateRules(
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
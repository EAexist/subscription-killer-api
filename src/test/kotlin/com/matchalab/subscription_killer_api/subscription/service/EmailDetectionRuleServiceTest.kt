package com.matchalab.subscription_killer_api.subscription.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension

private val logger = KotlinLogging.logger {}

@ExtendWith(MockitoExtension::class)
class EmailDetectionRuleServiceTest() {

    private val emailCategorizationPromptService = mockk<EmailCategorizationPromptService>()
    private val emailTemplateExtractionPromptService = mockk<EmailTemplateExtractionPromptService>()

    private val emailDetectionRuleService = EmailDetectionRuleService(
        emailCategorizationPromptService,
        emailTemplateExtractionPromptService
    )

    @BeforeEach
    fun setUp() {
        // Given
    }

//    @Test
//    fun `given incomplete rules and remaining chatclient quota then update via chatclient`() =
//        runTest {
//            every {
//                mockChatClientService.call<UpdateEmailDetectionRulesFromAIResultDto>(
//                    any<Resource>(),
//                    any<Map<String, Any>>(),
//                    any<Class<UpdateEmailDetectionRulesFromAIResultDto>>()
//                )
//            } returns UpdateEmailDetectionRulesFromAIResultDto(
//                mockNetflixEmailDetectionRule_SUBSCRIPTION_START,
//                mockNetflixEmailDetectionRule_SUBSCRIPTION_CANCEL, null, null
//            )
//
//            for (serviceProvider in listOf(
//                createServiceProvider(
//                    netflixDisplayName, mutableListOf(createEmailSource(netflixEmailAddress, null))
//                ),
//                createServiceProvider(
//                    netflixDisplayName, mutableListOf(
//                        createEmailSource(
//                            netflixEmailAddress, mutableMapOf(
//                                SubscriptionEventType.SUBSCRIPTION_START to mockNetflixEmailDetectionRule_SUBSCRIPTION_START
//                            )
//                        )
//                    )
//                )
//            )
//            ) {
//                // When
//                val updatedServiceProvider: Map<SubscriptionEventType, EmailDetectionRule> =
//                    emailDetectionRuleService.generateRules(
//                        serviceProvider.emailSources[0],
//                        fakeAddressToGmailMessages
//                    )
//
//                // Then
//                val expectedServiceProvider =
//                    createServiceProvider(
//                        netflixDisplayName, mutableListOf(
//                            createEmailSource(
//                                netflixEmailAddress, mutableMapOf(
//                                    SubscriptionEventType.SUBSCRIPTION_START to mockNetflixEmailDetectionRule_SUBSCRIPTION_START,
//                                    SubscriptionEventType.SUBSCRIPTION_CANCEL to mockNetflixEmailDetectionRule_SUBSCRIPTION_CANCEL
//                                )
//                            )
//                        )
//                    )
//
//                verify(atLeast = 1) {
//                    mockServiceProviderRepository.save(any<ServiceProvider>())
//                }
//                assertThat(updatedServiceProvider).isNotNull()
//                    .usingRecursiveComparison()
//                    .ignoringFields("id", "serviceProvider")
//                    .isEqualTo(expectedServiceProvider)
//                assertThat(updatedServiceProvider.id).isNotNull()
//                assertThat(updatedServiceProvider.emailSources)
//                    .allSatisfy { source ->
////                        assertThat(source.serviceProvider).isSameAs(updatedServiceProvider)
//                    }
//            }
//
//        }

}
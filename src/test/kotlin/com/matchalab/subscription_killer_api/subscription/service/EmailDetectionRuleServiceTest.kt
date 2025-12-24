package com.matchalab.subscription_killer_api.subscription.service

import com.matchalab.subscription_killer_api.ai.service.ChatClientService
import com.matchalab.subscription_killer_api.ai.service.config.PromptTemplateProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.core.io.ByteArrayResource

private val logger = KotlinLogging.logger {}

@ExtendWith(MockitoExtension::class)
class EmailDetectionRuleServiceTest() {

    private val mockChatClientService = mockk<ChatClientService>()
    private val promptTemplateProperties = mockk<PromptTemplateProperties>()

    private val emailDetectionRuleService = EmailDetectionRuleService(
        mockChatClientService,
        promptTemplateProperties
    )

    @BeforeEach
    fun setUp() {
        // Given
        val emptyResource = ByteArrayResource(ByteArray(0))
        every { promptTemplateProperties.filterAndCategorizeEmails } returns emptyResource
        every { promptTemplateProperties.generalizeStringPattern } returns emptyResource
        every { promptTemplateProperties.mergeEmailDetectionRules } returns emptyResource
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
//                mockNetflixEmailDetectionRule_PAID_SUBSCRIPTION_START,
//                mockNetflixEmailDetectionRule_PAID_SUBSCRIPTION_CANCEL, null, null
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
//                                SubscriptionEventType.PAID_SUBSCRIPTION_START to mockNetflixEmailDetectionRule_PAID_SUBSCRIPTION_START
//                            )
//                        )
//                    )
//                )
//            )
//            ) {
//                // When
//                val updatedServiceProvider: Map<SubscriptionEventType, EmailDetectionRule> =
//                    emailDetectionRuleService.updateRules(
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
//                                    SubscriptionEventType.PAID_SUBSCRIPTION_START to mockNetflixEmailDetectionRule_PAID_SUBSCRIPTION_START,
//                                    SubscriptionEventType.PAID_SUBSCRIPTION_CANCEL to mockNetflixEmailDetectionRule_PAID_SUBSCRIPTION_CANCEL
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
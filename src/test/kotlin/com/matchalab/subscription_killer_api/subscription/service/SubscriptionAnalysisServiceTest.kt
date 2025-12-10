package com.matchalab.subscription_killer_api.subscription.service

import com.google.api.services.gmail.model.Message
import com.matchalab.subscription_killer_api.repository.SubscriptionReportRepository
import com.matchalab.subscription_killer_api.subscription.ServiceProviderType
import com.matchalab.subscription_killer_api.subscription.SubscriptionReport
import com.matchalab.subscription_killer_api.subscription.config.ProviderConfiguration
import com.matchalab.subscription_killer_api.subscription.dto.SubscriptionEmailAnalysisResultDto
import com.matchalab.subscription_killer_api.subscription.dto.SubscriptionReportResponseDto
import com.matchalab.subscription_killer_api.subscription.dto.SubscriptionResponseDto
import com.matchalab.subscription_killer_api.subscription.providers.core.EmailAnalysisStrategy
import com.matchalab.subscription_killer_api.subscription.providers.core.PaymentCycle
import com.matchalab.subscription_killer_api.subscription.providers.core.PricingPlan
import com.matchalab.subscription_killer_api.subscription.providers.core.ProviderMetadata
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration

private val logger = KotlinLogging.logger {}

@ExtendWith(MockitoExtension::class)
@ContextConfiguration(classes = [ProviderConfiguration::class])
class SubscriptionAnalysisServiceTest() {

    @Autowired private lateinit var subscriptionAnalysisService: SubscriptionAnalysisService

    @Mock private lateinit var mockSubscriptionReportRepository: SubscriptionReportRepository
    @Mock private lateinit var mockNetflixEmailAnalysisStrategy: EmailAnalysisStrategy
    @Mock private lateinit var mockCoupangStrategy: EmailAnalysisStrategy

    private class MockNetflixProviderMetaData : ProviderMetadata {
        override val providerType = ServiceProviderType.NETFLIX
        override val displayName = "Netflix"
        override val paymentCycle = PaymentCycle.MONTHLY
        override val plans = mapOf("스탠다드 광고형" to PricingPlan("스탠다드 광고형", BigDecimal(5500), 1))
    }

    private class MockCoupangProviderMetaData : ProviderMetadata {
        override val providerType = ServiceProviderType.COUPANG
        override val displayName = "Coupang"
        override val paymentCycle = PaymentCycle.MONTHLY
        override val plans = mapOf("default" to PricingPlan("default", BigDecimal(4500), 1))
    }

    val FAKE_NETFLIX_providerType: ServiceProviderType = ServiceProviderType.NETFLIX
    val FAKE_NETFLIX_subscribedSince: LocalDate = LocalDate.now().minusMonths(6)
    val FAKE_NETFLIX_subscribedUntil: LocalDate? = null
    val FAKE_NETFLIX_subscriptionStartMessage: Message = Message()
    val FAKE_NETFLIX_plan: PricingPlan? = MockNetflixProviderMetaData().plans.values.first()

    val FAKE_COUPANG_providerType: ServiceProviderType = ServiceProviderType.COUPANG
    val FAKE_COUPANG_subscribedSince: LocalDate = LocalDate.now().minusMonths(12)
    val FAKE_COUPANG_subscribedUntil: LocalDate? = LocalDate.now().minusMonths(3)
    val FAKE_COUPANG_subscriptionStartMessage: Message = Message()
    val FAKE_COUPANG_plan: PricingPlan? = MockCoupangProviderMetaData().plans.values.first()

    private val mockMetadataMap: Map<ServiceProviderType, ProviderMetadata> =
            mapOf(
                    ServiceProviderType.NETFLIX to MockNetflixProviderMetaData(),
                    ServiceProviderType.COUPANG to MockCoupangProviderMetaData()
            )

    @BeforeEach
    fun setUp() {
        val mockStrategies = listOf(mockNetflixEmailAnalysisStrategy, mockCoupangStrategy)
        subscriptionAnalysisService =
                SubscriptionAnalysisService(
                        mockSubscriptionReportRepository,
                        mockStrategies,
                        mockMetadataMap
                )
    }

    @Test
    fun `given ProviderConfiguration should correctly create subscription report response dto`() =
            runTest {
                whenever(mockSubscriptionReportRepository.save(any<SubscriptionReport>()))
                        .thenAnswer { it.getArgument<SubscriptionReport>(0) }

                whenever(mockNetflixEmailAnalysisStrategy.analyze(any()))
                        .thenReturn(
                                SubscriptionEmailAnalysisResultDto(
                                        FAKE_NETFLIX_providerType,
                                        FAKE_NETFLIX_subscribedSince,
                                        FAKE_NETFLIX_subscribedUntil,
                                        FAKE_NETFLIX_subscriptionStartMessage,
                                        FAKE_NETFLIX_plan?.name,
                                )
                        )

                whenever(mockCoupangStrategy.analyze(any()))
                        .thenReturn(
                                SubscriptionEmailAnalysisResultDto(
                                        FAKE_COUPANG_providerType,
                                        FAKE_COUPANG_subscribedSince,
                                        FAKE_COUPANG_subscribedUntil,
                                        FAKE_COUPANG_subscriptionStartMessage,
                                        null
                                )
                        )

                val expectedSubscriptions: List<SubscriptionResponseDto> =
                        listOf(
                                SubscriptionResponseDto(
                                        FAKE_NETFLIX_providerType,
                                        FAKE_NETFLIX_subscribedSince,
                                        FAKE_NETFLIX_subscribedUntil,
                                        FAKE_NETFLIX_subscriptionStartMessage,
                                        FAKE_NETFLIX_plan
                                ),
                                SubscriptionResponseDto(
                                        FAKE_COUPANG_providerType,
                                        FAKE_COUPANG_subscribedSince,
                                        FAKE_COUPANG_subscribedUntil,
                                        FAKE_COUPANG_subscriptionStartMessage,
                                        FAKE_COUPANG_plan
                                )
                        )
                val result: SubscriptionReportResponseDto? = subscriptionAnalysisService.analyze()
                logger.debug { "[subscriptionAnalysisService.analyze] result: $result" }

                assertThat(result).isNotNull()

                assertThat(result?.subscriptions)
                        .usingRecursiveComparison()
                        .ignoringFields("id")
                        .isEqualTo(expectedSubscriptions)

                assertThat(result?.createdAt)
                        .isBetween(
                                LocalDateTime.now().minusMinutes(10),
                                LocalDateTime.now().plusMinutes(10)
                        )
            }
}

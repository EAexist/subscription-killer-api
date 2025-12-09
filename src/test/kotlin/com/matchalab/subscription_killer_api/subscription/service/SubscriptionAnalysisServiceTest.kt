package com.matchalab.subscription_killer_api.subscription.service

import com.google.api.services.gmail.model.Message
import com.matchalab.subscription_killer_api.subscription.ServiceProviderType
import com.matchalab.subscription_killer_api.subscription.dto.SubscriptionAnalysisResultResponseDto
import com.matchalab.subscription_killer_api.subscription.dto.SubscriptionEmailAnalysisResultDto
import com.matchalab.subscription_killer_api.subscription.dto.SubscriptionRecordResponseDto
import com.matchalab.subscription_killer_api.subscription.providers.core.EmailAnalysisStrategy
import com.matchalab.subscription_killer_api.subscription.providers.core.SubscriptionAnalysisStrategyFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations.openMocks
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

private val logger = KotlinLogging.logger {}

@ExtendWith(MockitoExtension::class)
class SubscriptionAnalysisServiceTest() {

    private lateinit var subscriptionAnalysisStrategyFactory: SubscriptionAnalysisStrategyFactory
    private lateinit var subscriptionAnalysisService: SubscriptionAnalysisService

    @Mock private lateinit var mockNetflixEmailAnalysisStrategy: EmailAnalysisStrategy
    @Mock private lateinit var mockCoupangStrategy: EmailAnalysisStrategy

    val FAKE_NETFLIX_serviceProviderType: ServiceProviderType = ServiceProviderType.NETFLIX
    val FAKE_NETFLIX_startDate: LocalDate = LocalDate.now().minusMonths(6)
    val FAKE_NETFLIX_subscriptionStartMessage: Message = Message()

    val FAKE_COUPANG_serviceProviderType: ServiceProviderType = ServiceProviderType.COUPANG
    val FAKE_COUPANG_startDate: LocalDate = LocalDate.now().minusMonths(12)
    val FAKE_COUPANG_subscriptionStartMessage: Message = Message()

    @BeforeEach
    fun setUp() = runTest {
        openMocks(this)

        val mockedStrategies = listOf(mockNetflixEmailAnalysisStrategy, mockCoupangStrategy)
        subscriptionAnalysisStrategyFactory = SubscriptionAnalysisStrategyFactory(mockedStrategies)
        subscriptionAnalysisService =
                SubscriptionAnalysisService(subscriptionAnalysisStrategyFactory)

        whenever(mockNetflixEmailAnalysisStrategy.analyze(any()))
                .thenReturn(
                        SubscriptionEmailAnalysisResultDto(
                                FAKE_NETFLIX_serviceProviderType,
                                FAKE_NETFLIX_startDate,
                                FAKE_NETFLIX_subscriptionStartMessage
                        )
                )

        whenever(mockNetflixEmailAnalysisStrategy.analyze(any()))
                .thenReturn(
                        SubscriptionEmailAnalysisResultDto(
                                FAKE_COUPANG_serviceProviderType,
                                FAKE_COUPANG_startDate,
                                FAKE_COUPANG_subscriptionStartMessage
                        )
                )
    }

    @Test
    fun `given SubscriptionAnalysisStrategyFactory, should correctly create subscription analysis result`() =
            runTest {
                val expectedSubscriptions: List<SubscriptionRecordResponseDto> =
                        listOf(
                                SubscriptionRecordResponseDto(
                                        null,
                                        FAKE_NETFLIX_serviceProviderType,
                                        FAKE_NETFLIX_startDate,
                                        5500,
                                        LocalDate.now().plusMonths(1)
                                ),
                                SubscriptionRecordResponseDto(
                                        null,
                                        FAKE_COUPANG_serviceProviderType,
                                        FAKE_COUPANG_startDate,
                                        5500,
                                        LocalDate.now().plusMonths(1)
                                )
                        )
                val result: SubscriptionAnalysisResultResponseDto? =
                        subscriptionAnalysisService.analyze()
                logger.debug { "[subscriptionAnalysisService.analyze] result: $result" }

                assertThat(result).isNotNull()

                assertThat(result?.subscriptions)
                        .usingRecursiveComparison()
                        .ignoringFields("id", "monthlyCostEstimate", "nextPaymentDay")
                        .isEqualTo(expectedSubscriptions)

                assertThat(result?.subscriptions?.get(0)?.monthlyCostEstimate).isNotNull()
                assertThat(result?.subscriptions?.get(0)?.nextPaymentDay).isNotNull()
                assertThat(result?.subscriptions?.get(1)?.monthlyCostEstimate).isNotNull()
                assertThat(result?.subscriptions?.get(1)?.nextPaymentDay).isNotNull()

                assertThat(result?.lastAnalysisTimestamp)
                        .isBetween(
                                LocalDateTime.now().minusHours(1),
                                LocalDateTime.now().plusHours(1)
                        )
            }
}

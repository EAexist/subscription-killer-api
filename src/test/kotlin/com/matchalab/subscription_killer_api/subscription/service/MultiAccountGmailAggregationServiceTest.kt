package com.matchalab.subscription_killer_api.subscription.service

import com.matchalab.subscription_killer_api.repository.AppUserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

private val logger = KotlinLogging.logger {}

@ExtendWith(MockitoExtension::class)
class MultiAccountGmailAggregationServiceTest() {

    @InjectMocks
    private lateinit var multiAccountGmailAggregationService:
            MultiAccountGmailAggregationServiceImpl

    @Mock private lateinit var appUserRepository: AppUserRepository
    // @Mock private lateinit var gmailClientFactory: GmailClientFactory
    // @Mock private lateinit var mockGmailClientAdapter: GmailClientAdapter
    // @Mock private lateinit var gmailClientAdapterMockB: GmailClientAdapter
    // @Mock private lateinit var gmailClientAdapterMockC: GmailClientAdapter

    private val FAKE_APP_USER_ID: UUID = UUID.fromString("11111111-2222-3333-4444-555555555555")

    private val FAKE_GOOGLE_ACCOUNT_SUBJECT_A = "FAKE_GOOGLE_ACCOUNT_SUBJECT_A"
    private val FAKE_GOOGLE_ACCOUNT_SUBJECT_B = "FAKE_GOOGLE_ACCOUNT_SUBJECT_B"
    private val FAKE_GOOGLE_ACCOUNT_SUBJECT_C = "FAKE_GOOGLE_ACCOUNT_SUBJECT_C"

    @BeforeEach
    fun setUp() {

        // Given
        whenever(appUserRepository.findGoogleAccountSubjectsByAppUserId(FAKE_APP_USER_ID))
                .thenReturn(
                        listOf(
                                FAKE_GOOGLE_ACCOUNT_SUBJECT_A,
                                FAKE_GOOGLE_ACCOUNT_SUBJECT_B,
                                FAKE_GOOGLE_ACCOUNT_SUBJECT_C
                        )
                )
    }

    @Test
    fun `given Authenticated Context, MultiAccountGmailAggregationService should list all of user's google accounts`() =
            runTest {

                // Given
                val principal = FAKE_APP_USER_ID.toString()
                SecurityContextHolder.getContext().authentication =
                        UsernamePasswordAuthenticationToken(
                                principal,
                                "{noop}",
                                listOf(SimpleGrantedAuthority("ROLE_USER"))
                        )

                // When
                val exactGoogleAccountSubjects: List<String> =
                        multiAccountGmailAggregationService.getGoogleAccountSubjects()

                // Then
                logger.debug {
                    "[multiAccountGmailAggregationService.listMessagesBySender] exactGoogleAccountSubjects: $exactGoogleAccountSubjects"
                }

                assertThat(exactGoogleAccountSubjects)
                        .isNotNull()
                        .containsOnly(
                                FAKE_GOOGLE_ACCOUNT_SUBJECT_A,
                                FAKE_GOOGLE_ACCOUNT_SUBJECT_B,
                                FAKE_GOOGLE_ACCOUNT_SUBJECT_C
                        )
            }
}

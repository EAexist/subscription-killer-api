package com.matchalab.subscription_killer_api.subscription.service

import com.matchalab.subscription_killer_api.repository.AppUserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.util.*

private val logger = KotlinLogging.logger {}

@ExtendWith(MockitoExtension::class)
class MultiAccountGmailAggregationServiceTest() {

    private val appUserRepository = mockk<AppUserRepository>()
    private val multiAccountGmailAggregationService = MultiAccountGmailAggregationServiceImpl(appUserRepository)

    private val FAKE_APP_USER_ID: UUID = UUID.fromString("11111111-2222-3333-4444-555555555555")

    private val FAKE_GOOGLE_ACCOUNT_SUBJECT_A = "FAKE_GOOGLE_ACCOUNT_SUBJECT_A"
    private val FAKE_GOOGLE_ACCOUNT_SUBJECT_B = "FAKE_GOOGLE_ACCOUNT_SUBJECT_B"
    private val FAKE_GOOGLE_ACCOUNT_SUBJECT_C = "FAKE_GOOGLE_ACCOUNT_SUBJECT_C"

    @BeforeEach
    fun setUp() {

        // Given
        every {
            appUserRepository.findGoogleAccountSubjectsByAppUserId(FAKE_APP_USER_ID)
        } returns
                listOf(
                    FAKE_GOOGLE_ACCOUNT_SUBJECT_A,
                    FAKE_GOOGLE_ACCOUNT_SUBJECT_B,
                    FAKE_GOOGLE_ACCOUNT_SUBJECT_C
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
                "[multiAccountGmailAggregationService.getGoogleAccountSubjects] exactGoogleAccountSubjects: $exactGoogleAccountSubjects"
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

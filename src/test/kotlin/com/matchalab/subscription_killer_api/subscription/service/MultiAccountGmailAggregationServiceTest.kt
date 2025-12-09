package com.matchalab.subscription_killer_api.subscription.service

import com.google.api.services.gmail.model.Message
import com.matchalab.subscription_killer_api.repository.AppUserRepository
import com.matchalab.subscription_killer_api.subscription.GmailClientFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
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

    @Mock private lateinit var gmailClientFactory: GmailClientFactory
    @Mock private lateinit var appUserRepository: AppUserRepository
    @Mock private lateinit var gmailClientAdapterMockA: GmailClientAdapter
    @Mock private lateinit var gmailClientAdapterMockB: GmailClientAdapter
    @Mock private lateinit var gmailClientAdapterMockC: GmailClientAdapter

    private val FAKE_SUBJECT_KEYWORD: String = "FAKE_SUBJECT_KEYWORD"
    private val FAKE_APP_USER_ID: UUID = UUID.fromString("11111111-2222-3333-4444-555555555555")

    private val FAKE_GOOGLE_ACCOUNT_SUBJECT_A = "FAKE_GOOGLE_ACCOUNT_SUBJECT_A"
    private val FAKE_GOOGLE_ACCOUNT_SUBJECT_B = "FAKE_GOOGLE_ACCOUNT_SUBJECT_B"
    private val FAKE_GOOGLE_ACCOUNT_SUBJECT_C = "FAKE_GOOGLE_ACCOUNT_SUBJECT_C"

    private val FAKE_MESSAGE_A_1: Message = Message()
    private val FAKE_MESSAGE_B_1: Message = Message()
    private val FAKE_MESSAGE_B_2: Message = Message()
    private val FAKE_MESSAGE_B_3: Message = Message()

    @BeforeEach
    fun setUp() {
        FAKE_MESSAGE_A_1.setId("FAKE_MESSAGE_A_1")
        FAKE_MESSAGE_B_1.setId("FAKE_MESSAGE_B_1")
        FAKE_MESSAGE_B_2.setId("FAKE_MESSAGE_B_2")
        FAKE_MESSAGE_B_3.setId("FAKE_MESSAGE_B_3")

        gmailClientAdapterMockA = mock(GmailClientAdapter::class.java)
        gmailClientAdapterMockB = mock(GmailClientAdapter::class.java)
        gmailClientAdapterMockC = mock(GmailClientAdapter::class.java)

        // Given
        whenever(appUserRepository.findGoogleAccountSubjectsByAppUserId(FAKE_APP_USER_ID))
                .thenReturn(
                        listOf(
                                FAKE_GOOGLE_ACCOUNT_SUBJECT_A,
                                FAKE_GOOGLE_ACCOUNT_SUBJECT_B,
                                FAKE_GOOGLE_ACCOUNT_SUBJECT_C
                        )
                )

        whenever(gmailClientFactory.createAdapter(FAKE_GOOGLE_ACCOUNT_SUBJECT_A))
                .thenReturn(gmailClientAdapterMockA)

        whenever(gmailClientFactory.createAdapter(FAKE_GOOGLE_ACCOUNT_SUBJECT_B))
                .thenReturn(gmailClientAdapterMockB)

        whenever(gmailClientFactory.createAdapter(FAKE_GOOGLE_ACCOUNT_SUBJECT_C))
                .thenReturn(gmailClientAdapterMockC)

        whenever(
                        gmailClientAdapterMockA.listMessagesBySenderAndSubject(
                                any<String>(),
                                any<String>(),
                                any<LocalDate>()
                        )
                )
                .thenReturn(listOf(FAKE_MESSAGE_A_1))

        whenever(
                        gmailClientAdapterMockB.listMessagesBySenderAndSubject(
                                any<String>(),
                                any<String>(),
                                any<LocalDate>()
                        )
                )
                .thenReturn(listOf(FAKE_MESSAGE_B_1, FAKE_MESSAGE_B_2, FAKE_MESSAGE_B_3))

        whenever(
                        gmailClientAdapterMockC.listMessagesBySenderAndSubject(
                                any<String>(),
                                any<String>(),
                                any<LocalDate>()
                        )
                )
                .thenReturn(listOf())
    }

    @Test
    fun `given GmailClientFactory, listMessagesBySenderAndSubject should correctly aggregate all GmailClientAdapters' result concurrently`() =
            runTest {

                // Given
                val principal = FAKE_APP_USER_ID.toString()
                SecurityContextHolder.getContext().authentication =
                        UsernamePasswordAuthenticationToken(
                                principal,
                                "{noop}",
                                listOf(SimpleGrantedAuthority("ROLE_USER"))
                        )

                val FAKE_SENDER_EMAIL: String = "info@account.netflix.com"
                val FAKE_SEARCH_AFTER_THIS_DATE: LocalDate = LocalDate.now()

                // When
                val result: List<Message>? =
                        multiAccountGmailAggregationService.listMessagesBySenderAndSubject(
                                FAKE_SENDER_EMAIL,
                                FAKE_SUBJECT_KEYWORD,
                                FAKE_SEARCH_AFTER_THIS_DATE
                        )

                // Then
                logger.debug {
                    "[multiAccountGmailAggregationService.listMessagesBySender] result: $result"
                }
                assertThat(result)
                        .isNotNull()
                        .containsOnly(
                                FAKE_MESSAGE_A_1,
                                FAKE_MESSAGE_B_1,
                                FAKE_MESSAGE_B_2,
                                FAKE_MESSAGE_B_3
                        )
            }
}

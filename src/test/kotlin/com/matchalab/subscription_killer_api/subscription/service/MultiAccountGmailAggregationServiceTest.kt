package com.matchalab.subscription_killer_api.subscription.service

import com.matchalab.subscription_killer_api.subscription.GmailClientFactory
import com.matchalab.subscription_killer_api.repository.AppUserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.InjectMocks
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.mockito.Mockito.`when`
import com.google.api.services.gmail.model.Message
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.mockito.kotlin.verify
import org.mockito.Mockito.mock
import org.mockito.ArgumentMatchers.any

private val logger = KotlinLogging.logger {}

class MultiAccountGmailAggregationServiceTest() {

    @InjectMocks
    private lateinit var multiAccountGmailAggregationService: MultiAccountGmailAggregationService

    @MockitoBean private lateinit var gmailClientFactory: GmailClientFactory
    @MockitoBean private lateinit var appUserRepository: AppUserRepository
    @MockitoBean private lateinit var gmailClientAdapterMockA: GmailClientAdapter
    @MockitoBean private lateinit var gmailClientAdapterMockB: GmailClientAdapter
    @MockitoBean private lateinit var gmailClientAdapterMockC: GmailClientAdapter

    private val FAKE_SUBJECT_KEYWORD: String = "FAKE_SUBJECT_KEYWORD"

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
        `when`(gmailClientFactory.createAdapter(FAKE_GOOGLE_ACCOUNT_SUBJECT_A))
                .thenReturn(gmailClientAdapterMockA)
                
        `when`(gmailClientFactory.createAdapter(FAKE_GOOGLE_ACCOUNT_SUBJECT_B))
                .thenReturn(gmailClientAdapterMockB)

        `when`(gmailClientFactory.createAdapter(FAKE_GOOGLE_ACCOUNT_SUBJECT_C))
                .thenReturn(gmailClientAdapterMockC)

        `when`(gmailClientAdapterMockA.listMessagesBySenderAndSubject(any(), any(), any()))
                .thenReturn(listOf(FAKE_MESSAGE_A_1))
                
        `when`(gmailClientAdapterMockB.listMessagesBySenderAndSubject(any(), any(), any()))
                .thenReturn(listOf(FAKE_MESSAGE_B_1, FAKE_MESSAGE_B_2, FAKE_MESSAGE_B_3))
                
        `when`(gmailClientAdapterMockC.listMessagesBySenderAndSubject(any(), any(), any()))
                .thenReturn(listOf())
    }

    @Test
    fun `given GmailClientFactory, listMessagesBySenderAndSubject should correctly aggregate all GmailClientAdapters' result concurrently`() = runTest {

        // Given
        val FAKE_SENDER_EMAIL: String ="info@account.netflix.com" , 
        val FAKE_SEARCH_AFTER_THIS_DATE: LocalDate = LocalDate.now()

        // When
        val result: List<Message>? = multiAccountGmailAggregationService.listMessagesBySenderAndSubject(FAKE_SENDER_EMAIL, FAKE_SUBJECT_KEYWORD, FAKE_SEARCH_AFTER_THIS_DATE)

        // Then
        logger.debug { "[multiAccountGmailAggregationService.listMessagesBySender] result: $result" }
        assertThat(result).isNotNull().containsOnly(FAKE_MESSAGE_A_1, FAKE_MESSAGE_B_1, FAKE_MESSAGE_B_2, FAKE_MESSAGE_B_3)
    }

    @Test
    fun `analysis_modules_should_be_applied_correctly_to_each_found_email_and_return_results`() {}
}

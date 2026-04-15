package com.matchalab.subscription_killer_api.subscription.service

import com.matchalab.subscription_killer_api.domain.AppUser
import com.matchalab.subscription_killer_api.domain.GoogleAccount
import com.matchalab.subscription_killer_api.domain.UserRoleType
import com.matchalab.subscription_killer_api.service.AppUserService
import com.matchalab.subscription_killer_api.service.GoogleAccountService
import com.matchalab.subscription_killer_api.subscription.Subscription
import com.matchalab.subscription_killer_api.subscription.controller.AppProperties
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class SubscriptionReportServiceTest {

    private val appUserService: AppUserService = mockk()
    private val googleAccountService: GoogleAccountService = mockk()
    private val appProperties = AppProperties(minRequestIntervalSeconds = 600)
    private val subscriptionReportService = SubscriptionReportService(
        appUserService = appUserService,
        googleAccountService = googleAccountService,
        appProperties = appProperties
    )

    private val testAppUserId = UUID.randomUUID()

    @Test
    fun `getUpdateEligibility should return eligible when no previous sync exists`() {
        // Given
        every { appUserService.findLastEmailSyncedAtByUserId(testAppUserId) } returns null

        // When
        val result = subscriptionReportService.getUpdateEligibility(testAppUserId)

        // Then
        assertTrue(result.canUpdate)
        assertNull(result.analyzedAt)
        assertNull(result.availableSince)
    }

    @Test
    fun `getUpdateEligibility should return not eligible when within interval`() {
        // Given
        val lastSyncedAt = Instant.now().minus(5, ChronoUnit.MINUTES)
        every { appUserService.findLastEmailSyncedAtByUserId(testAppUserId) } returns lastSyncedAt

        // When
        val result = subscriptionReportService.getUpdateEligibility(testAppUserId)

        // Then
        assertEquals(false, result.canUpdate)
        assertEquals(lastSyncedAt, result.analyzedAt)
        assertNotNull(result.availableSince)
        assertTrue(result.availableSince!!.isAfter(Instant.now()))
    }

    @Test
    fun `getUpdateEligibility should return eligible when interval has passed`() {
        // Given
        val lastSyncedAt = Instant.now().minus(12, ChronoUnit.HOURS)
        every { appUserService.findLastEmailSyncedAtByUserId(testAppUserId) } returns lastSyncedAt

        // When
        val result = subscriptionReportService.getUpdateEligibility(testAppUserId)

        // Then
        assertTrue(result.canUpdate)
        assertEquals(lastSyncedAt, result.analyzedAt)
        assertNotNull(result.availableSince)
        assertTrue(result.availableSince!!.isBefore(Instant.now()))
    }

    @Test
    fun `getReport should return null when no analyzed subscriptions exist`() {
        // Given
        every { googleAccountService.existsAnalyzedSubscriptionByAppUserId(testAppUserId) } returns false

        // When
        val result = subscriptionReportService.getReport(testAppUserId)

        // Then
        assertNull(result)
    }

    @Test
    fun `getReport should return report when analyzed subscriptions exist`() {
        // Given
        val now = Instant.now()
        val googleAccount1 =
            createMockGoogleAccount("subject1", "John Doe", "john@example.com", now)
        val googleAccount2 = createMockGoogleAccount(
            "subject2",
            "Jane Smith",
            "jane@example.com",
            now.minus(1, ChronoUnit.HOURS)
        )

        every { googleAccountService.existsAnalyzedSubscriptionByAppUserId(testAppUserId) } returns true
        every { appUserService.findGoogleAccountsWithFullSubscriptions(testAppUserId) } returns listOf(
            googleAccount1,
            googleAccount2
        )

        // When
        val result = subscriptionReportService.getReport(testAppUserId)

        // Then
        assertNotNull(result)
        assertEquals(2, result!!.accountReports.size)
        assertEquals(
            now.minus(1, ChronoUnit.HOURS),
            result.analyzedAt
        ) // Should return the earliest analyzedAt

        // Verify account reports contain expected data
        val account1 = result.accountReports.find { it.googleAccount.email == "john@example.com" }
        val account2 = result.accountReports.find { it.googleAccount.email == "jane@example.com" }

        assertNotNull(account1)
        assertNotNull(account2)
        assertEquals("John Doe", account1!!.googleAccount.name)
        assertEquals("Jane Smith", account2!!.googleAccount.name)
    }

    @Test
    fun `getReport should handle single Google account`() {
        // Given
        val now = Instant.now()
        val googleAccount =
            createMockGoogleAccount("subject1", "Single User", "single@example.com", now)

        every { googleAccountService.existsAnalyzedSubscriptionByAppUserId(testAppUserId) } returns true
        every { appUserService.findGoogleAccountsWithFullSubscriptions(testAppUserId) } returns listOf(
            googleAccount
        )

        // When
        val result = subscriptionReportService.getReport(testAppUserId)

        // Then
        assertNotNull(result)
        assertEquals(1, result!!.accountReports.size)
        assertEquals(now, result.analyzedAt)
        assertEquals("Single User", result.accountReports[0].googleAccount.name)
        assertEquals("single@example.com", result.accountReports[0].googleAccount.email)
    }

    @Test
    fun `getReport should handle Google account with no analyzedAt timestamp`() {
        // Given
        val googleAccount1 =
            createMockGoogleAccount("subject1", "User 1", "user1@example.com", null)
        val googleAccount2 =
            createMockGoogleAccount("subject2", "User 2", "user2@example.com", Instant.now())

        every { googleAccountService.existsAnalyzedSubscriptionByAppUserId(testAppUserId) } returns true
        every { appUserService.findGoogleAccountsWithFullSubscriptions(testAppUserId) } returns listOf(
            googleAccount1,
            googleAccount2
        )

        // When
        val result = subscriptionReportService.getReport(testAppUserId)

        // Then
        assertNotNull(result)
        assertEquals(2, result!!.accountReports.size)
        assertEquals(googleAccount2.analyzedAt, result.analyzedAt) // Should ignore null analyzedAt
    }

    private fun createMockGoogleAccount(
        subject: String,
        name: String,
        email: String,
        analyzedAt: Instant?
    ): GoogleAccount {
        val googleAccount = GoogleAccount(
            subject = subject,
            name = name,
            email = email,
            lastEmailSyncedAt = Instant.now()
        )
        googleAccount.analyzedAt = analyzedAt

        // Add mock subscriptions
        val subscription = Subscription(
            serviceProvider = mockk(relaxed = true),
            googleAccount = googleAccount
        )
        googleAccount.subscriptions.add(subscription)

        // Set up AppUser relationship
        googleAccount.appUser = AppUser(
            id = testAppUserId,
            name = "Test User",
            userRole = UserRoleType.USER,
            googleAccounts = mutableListOf(googleAccount)
        )

        return googleAccount
    }
}

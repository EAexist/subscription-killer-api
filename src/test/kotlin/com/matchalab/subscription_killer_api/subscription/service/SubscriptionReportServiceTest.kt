package com.matchalab.subscription_killer_api.subscription.service

import com.matchalab.subscription_killer_api.domain.AppUser
import com.matchalab.subscription_killer_api.domain.GoogleAccount
import com.matchalab.subscription_killer_api.domain.UserRoleType
import com.matchalab.subscription_killer_api.service.AppUserService
import com.matchalab.subscription_killer_api.subscription.dto.ReportUpdateEligibilityDto
import com.matchalab.subscription_killer_api.subscription.dto.ServiceProviderResponseDto
import com.matchalab.subscription_killer_api.subscription.dto.SubscriptionResponseDto
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*

class SubscriptionReportServiceTest {

    private val appUserService: AppUserService = mockk()
    private val subscriptionService: SubscriptionService = mockk()
    private val subscriptionReportService = SubscriptionReportService(
        appUserService = appUserService,
        subscriptionService = subscriptionService,
    )

    private val testAppUserId = UUID.randomUUID()

    @Test
    fun `getReport should return null when no analyzed subscriptions exist`() {
        // Given
        every { appUserService.getReportUpdateEligibility(testAppUserId) } returns ReportUpdateEligibilityDto(
            true,
            reportUpdatedAt = null,
            availableSince = null
        )
        every { subscriptionService.getResponseDtos(any()) } returns listOf(
        )

        // When
        val result = subscriptionReportService.getReport(testAppUserId)

        // Then
        assertNull(result)
    }

    @Test
    fun `getReport should return report when analyzed subscriptions exist`() {
        // Given
        val reportUpdatedAt = Instant.now().minusSeconds(600)
        val availableSince = reportUpdatedAt.plusSeconds(600)
        val googleAccount1 =
            createMockGoogleAccount(
                "subject1",
                "John Doe",
                "john@example.com",
                reportUpdatedAt
            )
        val googleAccount2 = createMockGoogleAccount(
            "subject2",
            "Jane Smith",
            "jane@example.com",
            reportUpdatedAt
        )

        every { appUserService.getReportUpdateEligibility(testAppUserId) } returns ReportUpdateEligibilityDto(
            true,
            reportUpdatedAt = reportUpdatedAt,
            availableSince = availableSince
        )
        every { appUserService.findGoogleAccountsWithFullSubscriptions(testAppUserId) } returns listOf(
            googleAccount1,
            googleAccount2
        )
        every { subscriptionService.getResponseDtos(any()) } returns listOf(
            createMockSubscriptionResponseDto()
        )

        // When
        val result = subscriptionReportService.getReport(testAppUserId)

        // Then
        assertNotNull(result)
        assertEquals(2, result!!.accountReports.size)
        assertEquals(
            availableSince,
            result.reportUpdateAvailableSince
        )

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
        val reportUpdatedAt = Instant.now().minusSeconds(600)
        val availableSince = reportUpdatedAt.plusSeconds(600)
        val googleAccount =
            createMockGoogleAccount(
                "subject1",
                "Single User",
                "single@example.com",
                reportUpdatedAt
            )

        every { appUserService.getReportUpdateEligibility(testAppUserId) } returns ReportUpdateEligibilityDto(
            true,
            reportUpdatedAt = reportUpdatedAt,
            availableSince = availableSince
        )
        every { appUserService.findGoogleAccountsWithFullSubscriptions(testAppUserId) } returns listOf(
            googleAccount
        )
        every { subscriptionService.getResponseDtos(any()) } returns listOf(
            createMockSubscriptionResponseDto()
        )

        // When
        val result = subscriptionReportService.getReport(testAppUserId)

        // Then
        assertNotNull(result)
        assertEquals(1, result!!.accountReports.size)
        assertEquals(
            availableSince,
            result.reportUpdateAvailableSince
        )
        assertEquals("Single User", result.accountReports[0].googleAccount.name)
        assertEquals("single@example.com", result.accountReports[0].googleAccount.email)
    }

    private fun createMockSubscriptionResponseDto(): SubscriptionResponseDto =
        SubscriptionResponseDto(
            id = UUID.randomUUID(),
            serviceProvider = ServiceProviderResponseDto(
                id = UUID.randomUUID(),
                displayName = "Test Service Provider",
                websiteUrl = "test.example.com",
                canAnalyzeSubscription = true,
                logoDevSuffix = null,
                subscriptionPageUrl = null,
            ),
            registeredSince = null,
            hasSubscribedNewsletterOrAd = false,
            subscribedSince = null,
            isNotSureIfSubscriptionIsOngoing = false,
        )

    private fun createMockGoogleAccount(
        subject: String,
        name: String,
        email: String,
        lastEmailSyncedAt: Instant? = null
    ): GoogleAccount {
        val googleAccount = GoogleAccount(
            subject = subject,
            name = name,
            email = email,
            lastEmailSyncedAt = lastEmailSyncedAt
        )

        // Add mock subscriptions
//        val subscription = Subscription(
//            id = UUID.randomUUID(),
//            serviceProvider = mockk(relaxed = true),
//            googleAccount = googleAccount
//        )
//        googleAccount.subscriptions.add(subscription)

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

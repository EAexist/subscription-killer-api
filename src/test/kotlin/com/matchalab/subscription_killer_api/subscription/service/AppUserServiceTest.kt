package com.matchalab.sublog_api.subscription.service

import com.matchalab.sublog_api.controller.AppProperties
import com.matchalab.sublog_api.guest.GuestAppUserProperties
import com.matchalab.sublog_api.repository.AppUserRepository
import com.matchalab.sublog_api.service.AppUserService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class AppUserServiceTest {

    val appUserRepository = mockk<AppUserRepository>()

    private val appUserService: AppUserService = AppUserService(
        appProperties = AppProperties(minRequestIntervalSeconds = 600),
        guestAppUserProperties = GuestAppUserProperties(
            "TEST_GUEST",
            listOf("TEST_GUEST_EMAIL_FIRST@example.com", "TEST_GUEST_EMAIL_SECONDS@example.com"),
        ),
        appUserRepository = appUserRepository
    )
    private val testAppUserId = UUID.randomUUID()

    @Test
    fun `getUpdateEligibility should return eligible when no previous sync exists`() {
        // Given
        every { appUserRepository.findLastReportGeneratedAtByUserId(testAppUserId) } returns null

        // When
        val result = appUserService.getReportUpdateEligibility(testAppUserId)

        // Then
        assertTrue(result.canUpdate)
        assertNull(result.reportUpdatedAt)
        assertNull(result.availableSince)
    }

    @Test
    fun `getUpdateEligibility should return not eligible when within interval`() {
        // Given
        val reportUpdatedAt = Instant.now().minus(5, ChronoUnit.MINUTES)
        every { appUserRepository.findLastReportGeneratedAtByUserId(testAppUserId) } returns reportUpdatedAt

        // When
        val result = appUserService.getReportUpdateEligibility(testAppUserId)

        // Then
        assertEquals(false, result.canUpdate)
        assertEquals(reportUpdatedAt, result.reportUpdatedAt)
        assertNotNull(result.availableSince)
        assertTrue(result.availableSince!!.isAfter(Instant.now()))
    }

    @Test
    fun `getUpdateEligibility should return eligible when interval has passed`() {
        // Given
        val reportUpdatedAt = Instant.now().minus(12, ChronoUnit.HOURS)
        every { appUserRepository.findLastReportGeneratedAtByUserId(testAppUserId) } returns reportUpdatedAt

        // When
        val result = appUserService.getReportUpdateEligibility(testAppUserId)

        // Then
        assertTrue(result.canUpdate)
        assertEquals(reportUpdatedAt, result.reportUpdatedAt)
        assertNotNull(result.availableSince)
        assertTrue(result.availableSince!!.isBefore(Instant.now()))
    }
}

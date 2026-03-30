package com.matchalab.subscription_killer_api.subscription

import com.matchalab.subscription_killer_api.domain.GoogleAccount
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class SubscriptionExtensionsTest {

    @Test
    fun `isCanceled should return true when latest SUBSCRIPTION_CANCEL is more recent than other events`() {
        // Given
        val subscription = Subscription(
            serviceProvider = mockServiceProvider(),
            googleAccount = mockGoogleAccount()
        )

        val now = Instant.now()
        val yesterday = now.minus(1, ChronoUnit.DAYS)
        val twoDaysAgo = now.minus(2, ChronoUnit.DAYS)

        subscription.subscriptionEvents = mutableListOf(
            SubscriptionEvent(twoDaysAgo, SubscriptionEventType.SUBSCRIPTION_CANCEL),
            SubscriptionEvent(now, SubscriptionEventType.SUBSCRIPTION_CANCEL),
            SubscriptionEvent(yesterday, SubscriptionEventType.MONTHLY_PAYMENT),
            SubscriptionEvent(now.minus(3, ChronoUnit.DAYS), SubscriptionEventType.SUBSCRIPTION_CANCEL)
        )

        // When
        val result = subscription.isCanceled()

        // Then
        assertEquals(true, result)
    }

    @Test
    fun `isCanceled should return false when no SUBSCRIPTION_CANCEL events`() {
        // Given
        val subscription = Subscription(
            serviceProvider = mockServiceProvider(),
            googleAccount = mockGoogleAccount()
        )

        val now = Instant.now()
        subscription.subscriptionEvents = mutableListOf(
            SubscriptionEvent(now, SubscriptionEventType.MONTHLY_PAYMENT),
            SubscriptionEvent(now.minus(1, ChronoUnit.DAYS), SubscriptionEventType.ANNUAL_PAYMENT),
            SubscriptionEvent(now.minus(2, ChronoUnit.DAYS), SubscriptionEventType.SUBSCRIPTION_START)
        )

        // When
        val result = subscription.isCanceled()

        // Then
        assertEquals(false, result)
    }

    @Test
    fun `getLatestSubscriptionStartDate should return latest SUBSCRIPTION_START event date`() {
        // Given
        val subscription = Subscription(
            serviceProvider = mockServiceProvider(),
            googleAccount = mockGoogleAccount()
        )

        val now = Instant.now()
        val yesterday = now.minus(1, ChronoUnit.DAYS)
        val twoDaysAgo = now.minus(2, ChronoUnit.DAYS)

        subscription.subscriptionEvents = mutableListOf(
            SubscriptionEvent(twoDaysAgo, SubscriptionEventType.SUBSCRIPTION_START),
            SubscriptionEvent(now, SubscriptionEventType.SUBSCRIPTION_START),
            SubscriptionEvent(yesterday, SubscriptionEventType.MONTHLY_PAYMENT),
            SubscriptionEvent(now.minus(3, ChronoUnit.DAYS), SubscriptionEventType.SUBSCRIPTION_START)
        )

        // When
        val result = subscription.getLatestSubscriptionStartDate()

        // Then
        assertEquals(now, result)
    }

    @Test
    fun `getLatestSubscriptionStartDate should return null when no SUBSCRIPTION_START events`() {
        // Given
        val subscription = Subscription(
            serviceProvider = mockServiceProvider(),
            googleAccount = mockGoogleAccount()
        )

        val now = Instant.now()
        subscription.subscriptionEvents = mutableListOf(
            SubscriptionEvent(now, SubscriptionEventType.MONTHLY_PAYMENT),
            SubscriptionEvent(now.minus(1, ChronoUnit.DAYS), SubscriptionEventType.ANNUAL_PAYMENT),
            SubscriptionEvent(now.minus(2, ChronoUnit.DAYS), SubscriptionEventType.NOT_A_SUBSCRIPTION_EMAIL)
        )

        // When
        val result = subscription.getLatestSubscriptionStartDate()

        // Then
        assertNull(result)
    }

    @Test
    fun `getLatestSubscriptionCancelDate should return latest SUBSCRIPTION_CANCEL event date`() {
        // Given
        val subscription = Subscription(
            serviceProvider = mockServiceProvider(),
            googleAccount = mockGoogleAccount()
        )

        val now = Instant.now()
        val yesterday = now.minus(1, ChronoUnit.DAYS)
        val twoDaysAgo = now.minus(2, ChronoUnit.DAYS)

        subscription.subscriptionEvents = mutableListOf(
            SubscriptionEvent(twoDaysAgo, SubscriptionEventType.SUBSCRIPTION_CANCEL),
            SubscriptionEvent(now, SubscriptionEventType.SUBSCRIPTION_CANCEL),
            SubscriptionEvent(yesterday, SubscriptionEventType.MONTHLY_PAYMENT),
            SubscriptionEvent(now.minus(3, ChronoUnit.DAYS), SubscriptionEventType.SUBSCRIPTION_CANCEL)
        )

        // When
        val result = subscription.getLatestSubscriptionCancelDate()

        // Then
        assertEquals(now, result)
    }

    @Test
    fun `getLatestSubscriptionCancelDate should return null when no SUBSCRIPTION_CANCEL events`() {
        // Given
        val subscription = Subscription(
            serviceProvider = mockServiceProvider(),
            googleAccount = mockGoogleAccount()
        )

        val now = Instant.now()
        subscription.subscriptionEvents = mutableListOf(
            SubscriptionEvent(now, SubscriptionEventType.MONTHLY_PAYMENT),
            SubscriptionEvent(now.minus(1, ChronoUnit.DAYS), SubscriptionEventType.ANNUAL_PAYMENT),
            SubscriptionEvent(now.minus(2, ChronoUnit.DAYS), SubscriptionEventType.SUBSCRIPTION_START)
        )

        // When
        val result = subscription.getLatestSubscriptionCancelDate()

        // Then
        assertNull(result)
    }

    @Test
    fun `getLatestMonthlySubscriptionDate should return latest MONTHLY_PAYMENT event date`() {
        // Given
        val subscription = Subscription(
            serviceProvider = mockServiceProvider(),
            googleAccount = mockGoogleAccount()
        )

        val now = Instant.now()
        val yesterday = now.minus(1, ChronoUnit.DAYS)
        val twoDaysAgo = now.minus(2, ChronoUnit.DAYS)

        subscription.subscriptionEvents = mutableListOf(
            SubscriptionEvent(twoDaysAgo, SubscriptionEventType.MONTHLY_PAYMENT),
            SubscriptionEvent(now, SubscriptionEventType.MONTHLY_PAYMENT),
            SubscriptionEvent(yesterday, SubscriptionEventType.SUBSCRIPTION_START),
            SubscriptionEvent(now.minus(3, ChronoUnit.DAYS), SubscriptionEventType.MONTHLY_PAYMENT)
        )

        // When
        val result = subscription.getLatestMonthlySubscriptionDate()

        // Then
        assertEquals(now, result)
    }

    @Test
    fun `getLatestMonthlySubscriptionDate should return null when no MONTHLY_PAYMENT events`() {
        // Given
        val subscription = Subscription(
            serviceProvider = mockServiceProvider(),
            googleAccount = mockGoogleAccount()
        )

        val now = Instant.now()
        subscription.subscriptionEvents = mutableListOf(
            SubscriptionEvent(now, SubscriptionEventType.SUBSCRIPTION_START),
            SubscriptionEvent(now.minus(1, ChronoUnit.DAYS), SubscriptionEventType.ANNUAL_PAYMENT),
            SubscriptionEvent(now.minus(2, ChronoUnit.DAYS), SubscriptionEventType.NOT_A_SUBSCRIPTION_EMAIL)
        )

        // When
        val result = subscription.getLatestMonthlySubscriptionDate()

        // Then
        assertNull(result)
    }

    @Test
    fun `getFirstOfConsecutiveMonthlySubscriptionDate should return first date of consecutive monthly payments`() {
        // Given
        val subscription = Subscription(
            serviceProvider = mockServiceProvider(),
            googleAccount = mockGoogleAccount()
        )

        val now = Instant.now()
        val oneMonthAgo = now.minus(30, ChronoUnit.DAYS)
        val twoMonthsAgo = now.minus(60, ChronoUnit.DAYS)
        val threeMonthsAgo = now.minus(90, ChronoUnit.DAYS)
        val fourMonthsAgo = now.minus(136, ChronoUnit.DAYS) // This should break the consecutive chain

        subscription.subscriptionEvents = mutableListOf(
            SubscriptionEvent(now, SubscriptionEventType.MONTHLY_PAYMENT),
            SubscriptionEvent(oneMonthAgo, SubscriptionEventType.MONTHLY_PAYMENT),
            SubscriptionEvent(twoMonthsAgo, SubscriptionEventType.MONTHLY_PAYMENT),
            SubscriptionEvent(threeMonthsAgo, SubscriptionEventType.MONTHLY_PAYMENT),
            SubscriptionEvent(fourMonthsAgo, SubscriptionEventType.MONTHLY_PAYMENT), // Too old, should break chain
            SubscriptionEvent(now.minus(10, ChronoUnit.DAYS), SubscriptionEventType.SUBSCRIPTION_START)
        )

        // When
        val result = subscription.getFirstOfConsecutiveMonthlySubscriptionDate()

        // Then
        assertEquals(threeMonthsAgo, result) // Should return the first in the consecutive chain
    }

    @Test
    fun `getFirstOfConsecutiveMonthlySubscriptionDate should return null when no monthly payments`() {
        // Given
        val subscription = Subscription(
            serviceProvider = mockServiceProvider(),
            googleAccount = mockGoogleAccount()
        )

        val now = Instant.now()
        subscription.subscriptionEvents = mutableListOf(
            SubscriptionEvent(now, SubscriptionEventType.SUBSCRIPTION_START),
            SubscriptionEvent(now.minus(1, ChronoUnit.DAYS), SubscriptionEventType.ANNUAL_PAYMENT),
            SubscriptionEvent(now.minus(2, ChronoUnit.DAYS), SubscriptionEventType.NOT_A_SUBSCRIPTION_EMAIL)
        )

        // When
        val result = subscription.getFirstOfConsecutiveMonthlySubscriptionDate()

        // Then
        assertNull(result)
    }

    @Test
    fun `getFirstOfConsecutiveMonthlySubscriptionDate should return null when monthly payments are too old`() {
        // Given
        val subscription = Subscription(
            serviceProvider = mockServiceProvider(),
            googleAccount = mockGoogleAccount()
        )

        val now = Instant.now()
        val fourMonthsAgo = now.minus(120, ChronoUnit.DAYS) // Too old

        subscription.subscriptionEvents = mutableListOf(
            SubscriptionEvent(fourMonthsAgo, SubscriptionEventType.MONTHLY_PAYMENT),
            SubscriptionEvent(now.minus(150, ChronoUnit.DAYS), SubscriptionEventType.MONTHLY_PAYMENT)
        )

        // When
        val result = subscription.getFirstOfConsecutiveMonthlySubscriptionDate()

        // Then
        assertNull(result)
    }

    private fun mockServiceProvider(): ServiceProvider {
        return ServiceProvider(
            displayName = "Test Provider",
            logoDevSuffix = null,
            websiteUrl = "https://example.com",
            subscriptionPageUrl = null
        )
    }

    private fun mockGoogleAccount(): GoogleAccount {
        return GoogleAccount(
            "",
            "",
            "",
        )
    }
}
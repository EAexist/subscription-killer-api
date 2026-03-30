package com.matchalab.subscription_killer_api.subscription

import com.matchalab.subscription_killer_api.subscription.dto.SubscriptionResponseDto
import com.matchalab.subscription_killer_api.utils.toDto
import java.time.Instant
import java.time.temporal.ChronoUnit

data class SubscribedSinceDto(
    val subscribedSince: Instant?,
    val isNotSureIfSubscriptionIsOngoing: Boolean = false,
)

fun Subscription.toResponseDto(): SubscriptionResponseDto {
    val subscribedSinceDto: SubscribedSinceDto = this.subscribedSince()
    return SubscriptionResponseDto(
        serviceProvider = this.serviceProvider.toDto(),
        registeredSince = this.registeredSince,
        hasSubscribedNewsletterOrAd = false,
        subscribedSince = subscribedSinceDto.subscribedSince,
        isNotSureIfSubscriptionIsOngoing = subscribedSinceDto.isNotSureIfSubscriptionIsOngoing,
    )
}

fun Subscription.subscribedSince(): SubscribedSinceDto {
    val serviceProvider = this.serviceProvider
    var latestStartDay: Instant?
    var latestCancelDay: Instant?

    if (!(serviceProvider.isSubscriptionEventRuleAvailable())) {
        return SubscribedSinceDto(subscribedSince = null)
    }

    // StartRule or MonthlyPayment Rule Exists.

    if (subscriptionEvents.isEmpty()) return SubscribedSinceDto(subscribedSince = null)

    if (!(serviceProvider.isSubscriptionEventRuleComplete())) {
        // Only StartRule Exists
        latestStartDay = getLatestSubscriptionStartDate()
        return SubscribedSinceDto(latestStartDay, true)
    }

    // One of StartRule+CancelRule or MonthlyPayment Rule Exists.

    if (serviceProvider.isSubscriptionStartRulePresent() && serviceProvider.isSubscriptionCancelRulePresent()) {
        // StartRule+CancelRule Exists.
        latestStartDay = getLatestSubscriptionStartDate()
        latestCancelDay = getLatestSubscriptionCancelDate()

        if ((latestStartDay != null) && ((latestCancelDay == null) || (latestStartDay.isAfter(latestCancelDay)))) {
            // Has Start Message. Has No Cancel Message After Start Message.
            return SubscribedSinceDto(latestStartDay)
        }
        // No Start Message after Last Cancel Message.
        return SubscribedSinceDto(null)
    }

    // StartRule+CancelRule Doesn't Exist. MonthlyPayment Rule Exists.
    val oldestConsecutive = getFirstOfConsecutiveMonthlySubscriptionDate()
    if (oldestConsecutive != null) {
        // Has Monthly Payment Message.
        return SubscribedSinceDto(oldestConsecutive)
    }
    // No Monthly Payment Message.
    return SubscribedSinceDto(null)
}

fun Subscription.isCanceled(): Boolean {
    val cancelDate = getLatestSubscriptionCancelDate()
    val startDate = getLatestSubscriptionStartDate()
    val monthlyDate = getLatestMonthlySubscriptionDate()
    
    return if (cancelDate != null) {
        val cancelAfterStart = startDate == null || cancelDate.isAfter(startDate)
        val cancelAfterMonthly = monthlyDate == null || cancelDate.isAfter(monthlyDate)
        cancelAfterStart && cancelAfterMonthly
    } else {
        false
    }
}

fun Subscription.getLatestSubscriptionStartDate(
): Instant? {
    val startEvents = subscriptionEvents
        .filter { it.type == SubscriptionEventType.SUBSCRIPTION_START }

    return startEvents.mapNotNull { it.internalDate }.maxOfOrNull { it }
}

fun Subscription.getLatestSubscriptionCancelDate(
): Instant? {
    val cancelEvents = subscriptionEvents
        .filter { it.type == SubscriptionEventType.SUBSCRIPTION_CANCEL }

    return cancelEvents.mapNotNull { it.internalDate }.maxOfOrNull { it }
}

fun Subscription.getLatestMonthlySubscriptionDate(
): Instant? {
    val cancelEvents = subscriptionEvents
        .filter { it.type == SubscriptionEventType.MONTHLY_PAYMENT }

    return cancelEvents.mapNotNull { it.internalDate }.maxOfOrNull { it }
}

fun Subscription.getFirstOfConsecutiveMonthlySubscriptionDate(
): Instant? {
    val monthlyPaymentEvents = subscriptionEvents
        .filter { it.type == SubscriptionEventType.MONTHLY_PAYMENT }
        .map {it.internalDate}
        .sortedByDescending { it }

    if (monthlyPaymentEvents.isEmpty()) {
        return null
    }

    val latestDate = monthlyPaymentEvents.first()?: return null

    if (isBeforeLastMonth(latestDate)) {
        return null
    }

    var consecutiveStartDate = latestDate

    for (i in 0 until monthlyPaymentEvents.size - 1) {
        val currentDate = monthlyPaymentEvents[i] ?: return null
        val olderDate = monthlyPaymentEvents[i + 1] ?: return null

        if (isBeforeLastMonth(olderDate, currentDate)) {
            break
        }
        consecutiveStartDate = olderDate
    }

    return consecutiveStartDate
}

fun Subscription.isBeforeLastMonth(date: Instant, referenceDate: Instant = Instant.now()): Boolean {
    val oneMonthAgo = referenceDate.minus(30, ChronoUnit.DAYS)
    return date.isBefore(oneMonthAgo)
}

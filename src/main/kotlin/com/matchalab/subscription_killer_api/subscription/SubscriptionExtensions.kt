package com.matchalab.subscription_killer_api.subscription

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

data class SubscribedSinceDto(
    val subscribedSince: Instant?,
    val nextPaymentDate: Instant? = null,
    val isNotSureIfSubscriptionIsOngoing: Boolean = false,
)

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
        return SubscribedSinceDto(latestStartDay, latestStartDay?.getNextMonthlyPaymentDate(), true)
    }

    // One of StartRule+CancelRule or MonthlyPayment Rule Exists.

    if (serviceProvider.isSubscriptionStartRulePresent() && serviceProvider.isSubscriptionCancelRulePresent()) {
        // StartRule+CancelRule Exists.
        latestStartDay = getLatestSubscriptionStartDate()
        latestCancelDay = getLatestSubscriptionCancelDate()

        if ((latestStartDay != null) && ((latestCancelDay == null) || (latestStartDay.isAfter(
                latestCancelDay
            )))
        ) {
            // Has Start Message. Has No Cancel Message After Start Message.
            return SubscribedSinceDto(latestStartDay, latestStartDay.getNextMonthlyPaymentDate())
        }
        // No Start Message after Last Cancel Message.
        return SubscribedSinceDto(null)
    }

    // StartRule+CancelRule Doesn't Exist. MonthlyPayment Rule Exists.
    val oldestConsecutive = getFirstOfConsecutiveMonthlySubscriptionDate()
    if (oldestConsecutive != null) {
        // Has Monthly Payment Message.
        return SubscribedSinceDto(oldestConsecutive, oldestConsecutive.getNextMonthlyPaymentDate())
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
        .filter { (it.type == SubscriptionEventType.SUBSCRIPTION_START_OR_PAYMENT) && !it.isMonthlyRecurring }

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
        .filter { (it.type == SubscriptionEventType.SUBSCRIPTION_START_OR_PAYMENT) && it.isMonthlyRecurring }

    return cancelEvents.mapNotNull { it.internalDate }.maxOfOrNull { it }
}

fun Subscription.getFirstOfConsecutiveMonthlySubscriptionDate(
): Instant? {
    val monthlyPaymentEvents = subscriptionEvents
        .filter { (it.type == SubscriptionEventType.SUBSCRIPTION_START_OR_PAYMENT) && it.isMonthlyRecurring }
        .map { it.internalDate }
        .sortedByDescending { it }

    if (monthlyPaymentEvents.isEmpty()) {
        return null
    }

    val latestDate = monthlyPaymentEvents.first() ?: return null

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

fun Instant.getNextMonthlyPaymentDate(): Instant {
    val now = Instant.now()
    val zone = ZoneId.systemDefault()

    val baseDay = this.atZone(zone).dayOfMonth
    val nowZ = now.atZone(zone)

    fun build(year: Int, month: Int): ZonedDateTime {
        val ym = YearMonth.of(year, month)
        val day = minOf(baseDay, ym.lengthOfMonth()) // handle Feb, etc.
        return ym.atDay(day).atStartOfDay(zone)
    }

    val thisMonth = build(nowZ.year, nowZ.monthValue)
    val next = if (thisMonth.toInstant().isAfter(now)) {
        thisMonth
    } else {
        val nm = nowZ.plusMonths(1)
        build(nm.year, nm.monthValue)
    }

    return next.toInstant()
}

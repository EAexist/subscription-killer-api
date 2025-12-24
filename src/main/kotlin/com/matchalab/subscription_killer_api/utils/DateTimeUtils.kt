package com.matchalab.subscription_killer_api.utils

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

object DateTimeUtils {

    fun minusMonthsFromInstant(originalInstant: Instant, monthsToSubtract: Long): Instant {
        val calculationZone: ZoneId = ZoneId.of("UTC")
        val zonedDateTime: ZonedDateTime = originalInstant.atZone(calculationZone)
        val targetZDT: ZonedDateTime = zonedDateTime.minusMonths(monthsToSubtract)
        val finalInstant: Instant = targetZDT.toInstant()

        return finalInstant
    }
    fun epochMilliToInstant(epochMilliseconds: Long): Instant {
        return Instant.ofEpochMilli(epochMilliseconds)
    }
}

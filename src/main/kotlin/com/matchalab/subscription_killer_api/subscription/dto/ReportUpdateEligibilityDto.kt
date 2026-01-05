package com.matchalab.subscription_killer_api.subscription.dto

import java.time.Instant

data class ReportUpdateEligibilityDto(
    val canUpdate: Boolean,
    val analyzedAt: Instant? = null,
    val availableSince: Instant? = null,
) {}

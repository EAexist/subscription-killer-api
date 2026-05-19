package com.matchalab.sublog_api.subscription.dto

import java.time.Instant

data class ReportUpdateEligibilityDto(
    val canUpdate: Boolean,
    val reportUpdatedAt: Instant? = null,
    val availableSince: Instant? = null,
) {}

package com.matchalab.subscription_killer_api.subscription.dto

import java.time.Instant

data class SubscriptionReportResponseDto(
    val accountReports: List<AccountReportDto>,
    val analyzedAt: Instant?
) {}

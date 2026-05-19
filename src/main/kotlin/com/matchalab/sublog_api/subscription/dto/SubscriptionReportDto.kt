package com.matchalab.sublog_api.subscription.dto

import java.time.Instant

data class SubscriptionReportResponseDto(
    val accountReports: List<AccountReportDto>,
    val reportUpdateAvailableSince: Instant,
) {}

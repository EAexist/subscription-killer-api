package com.matchalab.sublog_api.subscription.dto

import com.matchalab.sublog_api.core.dto.GoogleAccountResponseDto

data class AccountReportDto(
    val subscriptions: List<SubscriptionResponseDto>,
    val googleAccount: GoogleAccountResponseDto,
) {}

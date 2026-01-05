package com.matchalab.subscription_killer_api.subscription.dto

import com.matchalab.subscription_killer_api.core.dto.GoogleAccountResponseDto

data class AccountReportDto(
    val subscriptions: List<SubscriptionResponseDto>,
    val googleAccount: GoogleAccountResponseDto,
//        val analyzedAt: Instant?
) {}

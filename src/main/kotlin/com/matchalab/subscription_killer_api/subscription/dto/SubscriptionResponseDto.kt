package com.matchalab.subscription_killer_api.subscription.dto

import java.time.Instant

data class SubscriptionResponseDto(
    val serviceProvider: ServiceProviderDto,
    val registeredSince: Instant?,
    val hasSubscribedNewsletterOrAd: Boolean,
    val paidSince: Instant?,
    val isNotSureIfPaymentIsOngoing: Boolean,
) {}

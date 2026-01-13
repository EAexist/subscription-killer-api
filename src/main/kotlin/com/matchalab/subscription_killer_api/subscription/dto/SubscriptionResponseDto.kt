package com.matchalab.subscription_killer_api.subscription.dto

import java.time.Instant

data class SubscriptionResponseDto(
    val serviceProvider: ServiceProviderResponseDto,
    val registeredSince: Instant?,
    val hasSubscribedNewsletterOrAd: Boolean,
    val subscribedSince: Instant?,
    val isNotSureIfPaymentIsOngoing: Boolean,
) {}

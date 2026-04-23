package com.matchalab.subscription_killer_api.subscription.dto

import java.time.Instant
import java.util.*

data class SubscriptionResponseDto(
    val id: UUID,
    val serviceProvider: ServiceProviderResponseDto,
    val registeredSince: Instant?,
    val hasSubscribedNewsletterOrAd: Boolean,
    val subscribedSince: Instant?,
    val isNotSureIfSubscriptionIsOngoing: Boolean,
    val nextPaymentDate: Instant? = null,
) {}

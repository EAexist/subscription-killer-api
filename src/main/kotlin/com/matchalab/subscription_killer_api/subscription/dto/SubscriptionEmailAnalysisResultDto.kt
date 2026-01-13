package com.matchalab.subscription_killer_api.subscription.dto

import java.time.Instant
import java.util.*

data class SubscriptionEmailAnalysisResultDto(
    val serviceProviderId: UUID,
    val registeredSince: Instant,
    val hasSubscribedNewsletterOrAd: Boolean,
    val subscribedSince: Instant?,
    val isNotSureIfSubcriptionIsOngoing: Boolean,
) {}

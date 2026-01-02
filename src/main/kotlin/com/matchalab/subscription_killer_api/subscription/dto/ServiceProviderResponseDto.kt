package com.matchalab.subscription_killer_api.subscription.dto

import java.util.*

data class ServiceProviderResponseDto(
    val id: UUID,
    val displayName: String,
    val logoDevSuffix: String,
    val websiteUrl: String,
    val canAnalyzePayment: Boolean
) {}

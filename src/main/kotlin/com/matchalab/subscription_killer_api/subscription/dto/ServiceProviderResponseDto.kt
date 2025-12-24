package com.matchalab.subscription_killer_api.subscription.dto

import com.matchalab.subscription_killer_api.subscription.providers.core.PaymentCycle

data class ServiceProviderResponseDto(
        val serviceEmail: String,
        val displayName: String,
        val paymentCycle: PaymentCycle,
) {}

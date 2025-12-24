package com.matchalab.subscription_killer_api.subscription.providers.core

import java.math.BigDecimal

data class PricingPlan(
        val name: String,
        val annualPrice: BigDecimal,
        val maxSharedUsers: Int,
        val details: Map<String, Any> = mapOf()
)

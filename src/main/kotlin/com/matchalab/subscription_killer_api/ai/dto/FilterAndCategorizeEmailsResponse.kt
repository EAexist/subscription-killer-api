package com.matchalab.subscription_killer_api.ai.dto

data class FilterAndCategorizeEmailsResponse(
    val subscriptionStartMessageIds: List<String>,
    val subscriptionCancelMessageIds: List<String>,
    val monthlyPaymentMessageIds: List<String>,
    val annualPaymentMessageIds: List<String>,
)
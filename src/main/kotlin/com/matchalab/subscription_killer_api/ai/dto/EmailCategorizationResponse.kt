package com.matchalab.subscription_killer_api.ai.dto

import com.matchalab.subscription_killer_api.subscription.GmailMessage

data class EmailCategorizationResponse(
    val subscriptionStartMessageIds: List<String>,
    val subscriptionCancelMessageIds: List<String>,
    val monthlyPaymentMessageIds: List<String>,
    val annualPaymentMessageIds: List<String>,
)


fun EmailCategorizationResponse.toMessages(messages: List<GmailMessage>): List<GmailMessage> {
    val idToMessage = messages.associateBy { it.id }
    return listOf(
        this.subscriptionStartMessageIds,
        this.subscriptionCancelMessageIds,
        this.monthlyPaymentMessageIds,
        this.annualPaymentMessageIds,
    ).flatten().mapNotNull { idToMessage[it] }

}
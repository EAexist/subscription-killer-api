package com.matchalab.subscription_killer_api.ai.dto

import com.matchalab.subscription_killer_api.subscription.GmailMessage

data class EmailCategorizationResponse(
    val subsStartMsgIds: List<String>,
    val subsCancelMsgIds: List<String>,
    val monthlyMsgIds: List<String>,
    val annualMsgIds: List<String>,
)

fun EmailCategorizationResponse.toMessages(messages: List<GmailMessage>): List<GmailMessage> {
    val idToMessage = messages.associateBy { it.id }
    return listOf(
        this.subsStartMsgIds,
        this.subsCancelMsgIds,
        this.monthlyMsgIds,
        this.annualMsgIds,
    ).flatten().mapNotNull { idToMessage[it] }

}
package com.matchalab.subscription_killer_api.ai.dto

import com.matchalab.subscription_killer_api.subscription.GmailMessage

data class EmailCategorizationResponse(
    val subsStartMsgIds: List<String>? = listOf(),
    val subsCancelMsgIds: List<String>? = listOf(),
    val monthlyMsgIds: List<String>? = listOf(),
    val annualMsgIds: List<String>? = listOf(),
)


fun EmailCategorizationResponse.toMessages(messages: List<GmailMessage>): List<GmailMessage> {
    val idToMessage = messages.associateBy { it.id }
    return listOf(
        this.subsStartMsgIds ?: listOf(),
        this.subsCancelMsgIds ?: listOf(),
        this.monthlyMsgIds ?: listOf(),
        this.annualMsgIds ?: listOf(),
    ).flatten().mapNotNull { idToMessage[it] }

}
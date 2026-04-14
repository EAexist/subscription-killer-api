package com.matchalab.subscription_killer_api.ai.dto

import com.matchalab.subscription_killer_api.subscription.GmailMessage

data class EmailCategorizationResponse(
    val subsStartOrPaymentMsgIds: List<String>,
    val subsCancelMsgIds: List<String>,
    val nonSubsMsgIds: List<String>,
)

fun EmailCategorizationResponse.toMessages(messages: List<GmailMessage>): List<GmailMessage> {
    val idToMessage = messages.associateBy { it.id }
    return listOf(
        this.subsStartOrPaymentMsgIds,
        this.subsCancelMsgIds,
        this.nonSubsMsgIds,
    ).flatten().mapNotNull { idToMessage[it] }

}
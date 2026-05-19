package com.matchalab.sublog_api.ai.dto

import com.matchalab.sublog_api.subscription.GmailMessage
import com.matchalab.sublog_api.subscription.SubscriptionEventType

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

fun EmailCategorizationResponse.toMessagesWithSubscriptionEventType(messages: List<GmailMessage>): List<Pair<GmailMessage, SubscriptionEventType>> {
    val idToMessage = messages.associateBy { it.id }
    return listOf(
        this.subsStartOrPaymentMsgIds.mapNotNull { idToMessage[it] }
            .map { it to SubscriptionEventType.SUBSCRIPTION_START_OR_PAYMENT },
        this.subsCancelMsgIds.mapNotNull { idToMessage[it] }
            .map { it to SubscriptionEventType.SUBSCRIPTION_CANCEL },
        this.nonSubsMsgIds.mapNotNull { idToMessage[it] }
            .map { it to SubscriptionEventType.NOT_A_SUBSCRIPTION_EMAIL },
    ).flatten()
}
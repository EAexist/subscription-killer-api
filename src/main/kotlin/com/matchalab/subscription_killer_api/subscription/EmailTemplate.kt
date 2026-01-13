package com.matchalab.subscription_killer_api.subscription

import com.matchalab.subscription_killer_api.utils.containsPattern


data class EmailTemplate(
    val subjectRegex: String,
    val snippetRegex: String,
)

fun EmailTemplate.matchMessage(
    message: GmailMessage,
): Boolean {
    val subjectMatch: Boolean = message.subject.containsPattern(this.subjectRegex)
    val snippetMatch: Boolean = message.snippet.containsPattern(this.snippetRegex)
    return subjectMatch && snippetMatch
}

fun EmailTemplate.matchMessagesOrEmpty(messages: List<GmailMessage>): List<GmailMessage> {
    return messages.filter { message ->
        this.matchMessage(message)
    }
}





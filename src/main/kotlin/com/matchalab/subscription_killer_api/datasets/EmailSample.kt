package com.matchalab.subscription_killer_api.datasets

import com.fasterxml.jackson.annotation.JsonProperty
import com.matchalab.subscription_killer_api.subscription.EmailTemplate
import com.matchalab.subscription_killer_api.subscription.SubscriptionEventType

/**
 * Represents a sample email with its metadata
 */
data class EmailSample(
    @JsonProperty("message")
    val message: GmailApiMessage,
    @JsonProperty("subscriptionEventType")
    val subscriptionEventType: SubscriptionEventType,
    @JsonProperty("subjectRegex")
    val subjectRegex: String,
    @JsonProperty("snippetRegex")
    val snippetRegex: String
) {
    val emailTemplate: EmailTemplate
        get() = EmailTemplate(subjectRegex, snippetRegex)
}

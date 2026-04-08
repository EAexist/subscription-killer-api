package com.matchalab.subscription_killer_api.datasets

import com.fasterxml.jackson.annotation.JsonProperty
import com.matchalab.subscription_killer_api.emailtemplate.EmailTemplate
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.subscription.SubscriptionEventType

/**
 * Represents a sample email with its metadata
 */
data class EmailSample(
    @JsonProperty("message")
    val message: GmailMessage,
    @JsonProperty("subscriptionEventType")
    val subscriptionEventType: SubscriptionEventType,
    @JsonProperty("subjectAnchors")
    val subjectAnchors: List<String>,
    @JsonProperty("snippetAnchors")
    val snippetAnchors: List<String>
) {
    val template: EmailTemplate
        get() = EmailTemplate(subjectAnchors, snippetAnchors)
}

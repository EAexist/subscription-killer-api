package com.matchalab.subscription_killer_api.subscription

import java.time.Instant

data class GmailMessage(
    val id: String,
    val internalDate: Instant,
    val senderName: String?,
    val senderEmail: String,
    val subject: String,
    val snippet: String,
) {}

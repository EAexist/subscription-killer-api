package com.matchalab.sublog_api.subscription

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.Instant

data class GmailMessage(
    val id: String,
    @param:JsonFormat(
        shape = JsonFormat.Shape.NUMBER,
        timezone = "UTC"
    )
    val internalDate: Instant,
    val senderName: String?,
    val senderEmail: String,
    val subject: String,
    val snippet: String,
    val templateId: String? = null,
) {}

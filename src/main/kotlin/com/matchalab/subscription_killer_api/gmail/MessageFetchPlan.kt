package com.matchalab.subscription_killer_api.gmail

data class MessageFetchPlan(
    val format: String,
    val fields: String,
    val metadataHeaders: List<String> = emptyList()
) {
    companion object {
        val INTERNAL_DATE_SNIPPET_FROM_SUBJECT = MessageFetchPlan(
            format = "METADATA",
            fields = "id,internalDate,snippet,payload/headers",
            metadataHeaders = listOf("From", "Subject")
        )
        val FULL = MessageFetchPlan(format = "FULL", fields = "")
    }
}
package com.matchalab.subscription_killer_api.datasets

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/**
 * Represents Gmail API Message structure for JSON serialization
 */
data class GmailApiMessage(
    @JsonProperty("id")
    val id: String,
    
    @JsonProperty("internalDate")
    val internalDate: Long,
    
    @JsonProperty("snippet")
    val snippet: String,
    
    @JsonProperty("payload")
    val payload: Payload
)

/**
 * Represents Gmail API Message payload structure
 */
data class Payload(
    @JsonProperty("headers")
    val headers: List<Header>
)

/**
 * Represents Gmail API Message header structure
 */
data class Header(
    @JsonProperty("name")
    val name: String,
    
    @JsonProperty("value")
    val value: String
)

package com.matchalab.subscription_killer_api.ai

import com.matchalab.subscription_killer_api.subscription.GmailMessage
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

fun GmailMessage.toPromptParamString(index: Int): String =
    "${index}|${this.subject}|${this.snippet}"

fun String.parsePromptParamString(): List<Map<String, Any>> = 
    if (this.isBlank()) {
        emptyList()
    } else {
        this.split("\n").map {
            val output = it.split("|")
            mapOf(
                "index" to output[0].toInt() ,
                "subject" to output[1],
                "snippet" to output[2]
            )
        }
    }

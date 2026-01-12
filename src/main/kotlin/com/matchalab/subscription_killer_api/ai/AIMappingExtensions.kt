package com.matchalab.subscription_killer_api.ai

import com.matchalab.subscription_killer_api.subscription.GmailMessage
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

fun GmailMessage.toPromptParamString(): String =
    "${this.id}|${this.subject}|${this.snippet}"


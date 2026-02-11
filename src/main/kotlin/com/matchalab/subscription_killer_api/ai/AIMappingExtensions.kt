package com.matchalab.subscription_killer_api.ai

import com.matchalab.subscription_killer_api.subscription.GmailMessage
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

fun GmailMessage.toPromptParamString(index: Int): String =
    "${index}|${this.subject}|${this.snippet}"

package com.matchalab.subscription_killer_api.subscription.service

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

interface MultiAccountGmailAggregationService {
    fun getGoogleAccountSubjects(): List<String>
}

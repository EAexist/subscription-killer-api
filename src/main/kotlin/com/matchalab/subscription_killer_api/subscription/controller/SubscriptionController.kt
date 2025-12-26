package com.matchalab.subscription_killer_api.subscription.controller

import com.matchalab.subscription_killer_api.subscription.dto.SubscriptionReportResponseDto
import com.matchalab.subscription_killer_api.subscription.service.SubscriptionAnalysisService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/subscriptions")
class SubscriptionAnalysisController(
    private val subscriptionAnalysisService: SubscriptionAnalysisService
) {

    @GetMapping("/analysis")
    suspend fun analyze(): SubscriptionReportResponseDto {
        val result = subscriptionAnalysisService.analyze()
        logger.debug { result.toString() }
        return result
    }
}

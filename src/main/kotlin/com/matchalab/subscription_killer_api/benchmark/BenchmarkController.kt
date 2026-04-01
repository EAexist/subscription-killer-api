package com.matchalab.subscription_killer_api.benchmark

import com.matchalab.subscription_killer_api.config.AuthenticatedUser
import com.matchalab.subscription_killer_api.subscription.service.SubscriptionAnalysisService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/api/benchmark")
@Profile(
    "benchmark"
)
class BenchmarkController(
    private val subscriptionAnalysisService: SubscriptionAnalysisService,
) {

    private val logger = KotlinLogging.logger {}

    @PostMapping("/analyze")
//    @Transactional
    suspend fun analyze(@AuthenticatedUser appUserId: UUID): ResponseEntity<Unit> {

        try {
            subscriptionAnalysisService.analyze(appUserId)
            logger.debug { "BenchmarkController - analysis completed, returning OK" }
            return ResponseEntity.ok().build()
        } catch (e: Exception) {
            logger.debug(e) { "BenchmarkController - Exception during analysis: ${e.message}" }
            throw e
        }
    }
}

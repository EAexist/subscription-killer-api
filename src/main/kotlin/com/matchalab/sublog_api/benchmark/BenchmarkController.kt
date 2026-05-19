package com.matchalab.sublog_api.benchmark

import com.matchalab.sublog_api.config.AuthenticatedUser
import com.matchalab.sublog_api.subscription.service.SubscriptionAnalysisService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.kotlin.asContextElement
import io.micrometer.observation.ObservationRegistry
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/api/benchmark")
@Profile(
    "benchmark"
)
class BenchmarkController(
    private val subscriptionAnalysisService: SubscriptionAnalysisService,
    private val manager: BenchmarkTraceManager,
    private val observationRegistry: ObservationRegistry
) {

    private val logger = KotlinLogging.logger {}

    @PostMapping("/analyze")
//    @Transactional
    suspend fun analyze(@AuthenticatedUser appUserId: UUID): ResponseEntity<Unit> {

        val globalParentObservation = manager.globalParentObservation
            ?: throw IllegalStateException("Benchmark not started")

        return withContext(observationRegistry.asContextElement()) {
            globalParentObservation.openScope().use {
                try {
                    subscriptionAnalysisService.analyze(appUserId)
                    logger.debug { "BenchmarkController - analysis completed, returning OK" }
                    ResponseEntity.ok().build()
                } catch (e: Exception) {
                    logger.debug(e) { "BenchmarkController - Exception during analysis: ${e.message}" }
                    throw e
                }
            }
        }
    }

    @PostMapping("/start")
    fun startBenchmark(@RequestParam runId: String): Map<String, String> {
        val traceParent = manager.start(runId)
        return mapOf("traceparent" to traceParent)
    }
}

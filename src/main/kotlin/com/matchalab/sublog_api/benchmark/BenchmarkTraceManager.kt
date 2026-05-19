package com.matchalab.sublog_api.benchmark

import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.common.KeyValue
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import io.micrometer.tracing.Tracer
import jakarta.annotation.PreDestroy
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
@Profile("benchmark")
class BenchmarkTraceManager(
    private val tracer: Tracer,
    private val observationRegistry: ObservationRegistry
) {
    var globalParentObservation: Observation? = null

    fun start(runId: String): String {
        return tracer.withSpan(null).use {
            val newObservation = Observation.createNotStarted("benchmark_run $runId", observationRegistry)

            newObservation.context.apply {
                addLowCardinalityKeyValue(KeyValue.of("langfuse.tags", runId))
            }
            newObservation.start()

            val ids = newObservation.openScope().use {
                val span = tracer.currentSpan() ?: throw IllegalStateException("Tracer could not find an active span")
                "00-${span.context().traceId()}-${span.context().spanId()}-01"
            }

            globalParentObservation = newObservation
            ids
        }
    }

    fun stop() {
        globalParentObservation?.stop()
        globalParentObservation = null
    }

    @PreDestroy
    fun shutdown() {
        if (globalParentObservation != null) {
            logger.debug { "Spring Context shutting down: Finalizing active Benchmark Root..." }
            stop()
        }
    }
}

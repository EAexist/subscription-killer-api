package com.matchalab.sublog_api.benchmark

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Profile

@Profile("benchmark")
@ConfigurationProperties(prefix = "app.benchmark.mock-gmail-api")
data class BenchmarkProperties(
    val baseUrl: String
) {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    init {
        logger.info { "BenchmarkProperties initialized with baseUrl: $baseUrl" }
    }
}

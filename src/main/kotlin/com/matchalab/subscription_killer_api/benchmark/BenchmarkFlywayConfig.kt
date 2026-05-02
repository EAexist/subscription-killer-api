package com.matchalab.subscription_killer_api.benchmark

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile(
    "benchmark")
    class BenchmarkFlywayConfig {
    @Bean
    fun flywayMigrationStrategy() = FlywayMigrationStrategy { flyway ->
        flyway.clean()
        flyway.migrate()
    }
}
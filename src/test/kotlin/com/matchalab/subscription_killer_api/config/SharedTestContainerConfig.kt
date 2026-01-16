package com.matchalab.subscription_killer_api.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.testcontainers.containers.PostgreSQLContainer

@TestConfiguration
@Profile("ci")
class SharedTestcontainersConfig {
    @Bean
    @ServiceConnection
    fun postgres(): PostgreSQLContainer<*> = postgresInstance

    companion object {
        private val postgresInstance = PostgreSQLContainer("postgres:16-alpine").apply { start() }
    }
}
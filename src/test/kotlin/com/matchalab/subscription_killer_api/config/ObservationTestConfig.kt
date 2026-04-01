package com.matchalab.subscription_killer_api.config

import io.micrometer.observation.tck.TestObservationRegistry
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class ObservationTestConfig {
    @Bean
    fun observationRegistry(): TestObservationRegistry = TestObservationRegistry.create()
}
package com.matchalab.subscription_killer_api.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.matchalab.subscription_killer_api.datasets.EmailDatasetProvider
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.core.io.support.ResourcePatternResolver

@TestConfiguration
class DatasetTestConfiguration {

    @Bean
    fun testObjectMapper(): ObjectMapper = ObjectMapper().registerKotlinModule()

    @Bean
    fun testResourceResolver(): ResourcePatternResolver =
        PathMatchingResourcePatternResolver()

    @Bean
    fun emailDatasetProvider(
        objectMapper: ObjectMapper,
        resourceResolver: ResourcePatternResolver
    ): EmailDatasetProvider = EmailDatasetProvider( objectMapper, resourceResolver)
}
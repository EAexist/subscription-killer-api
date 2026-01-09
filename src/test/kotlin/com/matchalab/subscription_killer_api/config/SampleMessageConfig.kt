package com.matchalab.subscription_killer_api.config

import com.google.api.services.gmail.model.Message
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.io.ResourceLoader
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

@TestConfiguration
class SampleMessageConfig {
    @Bean
    fun testDataFactory() = TestDataFactory()

    @Bean
    fun sampleMessages(
        testDataFactory: TestDataFactory,
        loader: ResourceLoader,
        @Value("\${app.sample-messages.dir}") dir: String,
        @Value("\${app.sample-messages.fallback}") fallbackPath: String
    ): List<Message> {

        val resolver = PathMatchingResourcePatternResolver()
        val resources = resolver.getResources("classpath:$dir/*.json")

        if (resources.isEmpty()) {
            val resource = loader.getResource(fallbackPath)
            return testDataFactory.readMessages(resource.inputStream)
        }

        val allMessages: List<Message> = resources.flatMap { resource ->
            resource.inputStream.use { inputStream ->
                testDataFactory.readMessages(inputStream)
            }
        }
        return allMessages
    }
}
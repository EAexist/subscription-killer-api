package com.matchalab.subscription_killer_api.config

import com.google.api.services.gmail.model.Message
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.subscription.config.MailProperties
import com.matchalab.subscription_killer_api.utils.toGmailMessage
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.io.ResourceLoader
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

@TestConfiguration
class SampleMessageConfig(
    private val mailProperties: MailProperties,
) {
    @Bean
    fun testDataFactory() = TestDataFactory()

    @Bean
    fun sampleMessages(
        testDataFactory: TestDataFactory,
        loader: ResourceLoader,
        @Value("\${app.sample-messages.dir}") dir: String,
        @Value("\${app.sample-messages.fallback}") fallbackPath: String
    ): List<GmailMessage> {

        val resolver = PathMatchingResourcePatternResolver()
        val resources = resolver.getResources("classpath:$dir/*.json")
        var messages: List<Message>

        if (resources.isEmpty()) {
            val resource = loader.getResource(fallbackPath)
            messages = testDataFactory.readMessages(resource.inputStream)
        } else {
            messages = resources.flatMap { resource ->
                resource.inputStream.use { inputStream ->
                    testDataFactory.readMessages(inputStream)
                }
            }

        }
        return messages.map { it.toGmailMessage(mailProperties.maxSnippetSize) }
    }
}
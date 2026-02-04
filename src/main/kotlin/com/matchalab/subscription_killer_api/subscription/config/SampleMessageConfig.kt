package com.matchalab.subscription_killer_api.subscription.config

import com.google.api.services.gmail.model.Message
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.utils.readMessages
import com.matchalab.subscription_killer_api.utils.toGmailMessage
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ResourceLoader
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

@Configuration
class SampleMessageConfig(
    private val mailProperties: MailProperties,
) {

    @Bean
    fun sampleMessages(
        loader: ResourceLoader,
    ): List<GmailMessage> {
        return sampleRawMessages(
            loader
        ).map { it.toGmailMessage(mailProperties.maxSnippetSize) }
    }

    @Bean
    fun sampleRawMessages(
        loader: ResourceLoader
    ): List<Message> {

        val resolver = PathMatchingResourcePatternResolver()
        val resources = resolver.getResources("classpath:${mailProperties.samplesDir}/*.json")
        var messages: List<Message>

        if (resources.isEmpty()) {
            val resource = loader.getResource(mailProperties.samplesFallback)
            messages = readMessages(resource.inputStream)
        } else {
            messages = resources.flatMap { resource ->
                resource.inputStream.use { inputStream ->
                    readMessages(inputStream)
                }
            }

        }
        return messages
    }
}
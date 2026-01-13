package com.matchalab.subscription_killer_api.subscription.config

import com.google.api.services.gmail.model.Message
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.utils.readMessages
import com.matchalab.subscription_killer_api.utils.toGmailMessage
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ResourceLoader
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

@Configuration
//@Profile("!google-auth || !gmail")
class SampleMessageConfig(
    private val mailProperties: MailProperties,
) {

    @Bean
    fun sampleMessages(
        loader: ResourceLoader,
        @Value("\${app.sample-messages.dir}") dir: String,
        @Value("\${app.sample-messages.fallback}") fallbackPath: String
    ): List<GmailMessage> {

        val resolver = PathMatchingResourcePatternResolver()
        val resources = resolver.getResources("classpath:$dir/*.json")
        var messages: List<Message>

        if (resources.isEmpty()) {
            val resource = loader.getResource(fallbackPath)
            messages = readMessages(resource.inputStream)
        } else {
            messages = resources.flatMap { resource ->
                resource.inputStream.use { inputStream ->
                    readMessages(inputStream)
                }
            }

        }
        return messages.map { it.toGmailMessage(mailProperties.maxSnippetSize) }
    }
}
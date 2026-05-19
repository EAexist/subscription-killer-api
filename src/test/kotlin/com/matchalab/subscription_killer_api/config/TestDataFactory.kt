package com.matchalab.sublog_api.config

import com.google.api.services.gmail.model.Message
import com.matchalab.sublog_api.utils.readMessages
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

open class TestDataFactory(
) {
    fun loadSampleRawMessages(): List<Message> {
        val dir = "private/messages"
        val fallback = "static/messages/sample_messages_netflix_sketchfab.json"
        val resolver = PathMatchingResourcePatternResolver()
        val resources = resolver.getResources("classpath:$dir/*.json")
        var messages: List<Message>

        if (resources.isEmpty()) {
            val resource = ClassPathResource(fallback)
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
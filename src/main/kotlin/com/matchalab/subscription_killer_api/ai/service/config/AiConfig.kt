package com.matchalab.subscription_killer_api.ai.service.config

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Profile("ai || prod")
@Configuration
class AiConfig {

    @Bean
    fun chatClient(
        builder: ChatClient.Builder,
//        geminiTokenMetadataObservationAdvisor: GeminiTokenMetadataObservationAdvisor
    ): ChatClient {
        return builder
            .defaultAdvisors(SimpleLoggerAdvisor())
            .build()
    }
}
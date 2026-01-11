package com.matchalab.subscription_killer_api.ai.service.config

import org.springframework.ai.chat.client.ChatClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AiConfig {

    @Bean
    fun chatClient(
        builder: ChatClient.Builder,
//        geminiTokenMetadataObservationAdvisor: GeminiTokenMetadataObservationAdvisor
    ): ChatClient {
        return builder
//            .defaultAdvisors(geminiTokenMetadataObservationAdvisor)
            .build()
    }
}
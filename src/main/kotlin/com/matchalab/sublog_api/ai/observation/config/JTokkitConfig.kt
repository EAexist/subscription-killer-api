package com.matchalab.sublog_api.ai.observation.config

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.EncodingRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JTokkitConfig {

    @Bean
    fun encodingRegistry(): EncodingRegistry {
        return Encodings.newDefaultEncodingRegistry()
    }
}
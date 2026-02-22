package com.matchalab.subscription_killer_api.ai.observation

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
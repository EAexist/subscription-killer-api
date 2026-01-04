package com.matchalab.subscription_killer_api

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.event.EventListener
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import reactor.core.publisher.Hooks
import java.nio.charset.Charset

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableJpaRepositories("com.matchalab.subscription_killer_api.repository")
class SubscriptionKillerApiApplication {

    private val logger = KotlinLogging.logger {}

    @EventListener(ApplicationReadyEvent::class)
    fun logStartupInfo() {
        logger.info { "System Encoding: ${Charset.defaultCharset().displayName()}" }
        logger.info { "Encoding Test: 한글 ✅ ❌ ⚠️ 🔊 🔑 🔍" }
    }

    // /*
    //  * Create required HandlerMapping, to avoid several default HandlerMapping instances being
    // created
    //  */
    // @Bean
    // fun handlerMapping(): HandlerMapping {
    //     return RequestMappingHandlerMapping()
    // }

    // /*
    //  * Create required HandlerAdapter, to avoid several default HandlerAdapter instances being
    // created
    //  */
    // @Bean
    // fun handlerAdapter(): HandlerAdapter {
    //     return RequestMappingHandlerAdapter()
    // }
}

fun main(args: Array<String>) {
    Hooks.enableAutomaticContextPropagation()
    runApplication<SubscriptionKillerApiApplication>(*args)
}

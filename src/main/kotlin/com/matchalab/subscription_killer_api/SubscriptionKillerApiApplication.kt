package com.matchalab.subscription_killer_api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableJpaRepositories("com.matchalab.subscription_killer_api.repository")
class SubscriptionKillerApiApplication {
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
    runApplication<SubscriptionKillerApiApplication>(*args)
}

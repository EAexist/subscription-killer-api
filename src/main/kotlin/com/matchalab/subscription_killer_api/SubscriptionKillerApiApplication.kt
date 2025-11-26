package com.matchalab.subscription_killer_api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.servlet.HandlerAdapter

@SpringBootApplication
class SubscriptionKillerApiApplication {
    /*
     * Create required HandlerMapping, to avoid several default HandlerMapping instances being created
     */
    @Bean
    fun handlerMapping(): HandlerMapping {
        return RequestMappingHandlerMapping()
    }

    /*
     * Create required HandlerAdapter, to avoid several default HandlerAdapter instances being created
     */
    @Bean
    fun handlerAdapter(): HandlerAdapter {
        return RequestMappingHandlerAdapter()
    }
}

fun main(args: Array<String>) {
	runApplication<SubscriptionKillerApiApplication>(*args)
}

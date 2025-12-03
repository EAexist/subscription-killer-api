// https://docs.spring.io/spring-security/reference/servlet/configuration/kotlin.html
package com.matchalab.subscription_killer_api.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfigurationSource

@Configuration
@EnableWebSecurity(debug = true)
@EnableConfigurationProperties(CorsProperties::class)
open class WebSecurityConfig(
        val corsConfigurationSource: CorsConfigurationSource,
        val corsProperties: CorsProperties
) {

    @Bean
    open fun filterChain(
            http: HttpSecurity,
    ): SecurityFilterChain {
        http {
            cors { configurationSource = corsConfigurationSource }
            csrf { disable() }
            authorizeHttpRequests {
                authorize(HttpMethod.OPTIONS, "/**", permitAll)
                authorize(anyRequest, permitAll)
            }
        }
        return http.build()
    }
}

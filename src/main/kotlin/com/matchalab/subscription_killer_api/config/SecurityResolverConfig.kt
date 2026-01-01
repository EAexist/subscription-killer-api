package com.matchalab.subscription_killer_api.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver

@Configuration
class SecurityResolverConfig(
) {
    @Bean
    fun authenticationPrincipalArgumentResolver(): AuthenticationPrincipalArgumentResolver =
        AuthenticationPrincipalArgumentResolver()

}
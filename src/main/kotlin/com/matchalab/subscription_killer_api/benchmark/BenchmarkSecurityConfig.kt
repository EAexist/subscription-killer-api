package com.matchalab.subscription_killer_api.benchmark

import com.matchalab.subscription_killer_api.security.config.CorsProperties
import com.matchalab.subscription_killer_api.security.config.CustomSuccessHandler
import com.matchalab.subscription_killer_api.service.CustomOidcUserService
import com.matchalab.subscription_killer_api.service.MultiAccountOAuth2AuthorizedClientService
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@Profile(
    "benchmark || benchmark-dev")
    @EnableConfigurationProperties(CorsProperties::class)
    class BenchmarkSecurityConfig(
        private val corsProperties: CorsProperties,
        // Optional OAuth2 dependencies - will be null if not available
        private val multiAccountOAuth2AuthorizedClientService: MultiAccountOAuth2AuthorizedClientService?,
        private val customSuccessHandler: CustomSuccessHandler?,
        private val customOidcUserService: CustomOidcUserService?
    ) {

        @Bean("benchmarkSecurityFilterChain")
        fun filterChain(http: HttpSecurity): SecurityFilterChain {
            http {
                securityMatcher("/benchmark/**", "/actuator/**")
                cors {
                    configurationSource = corsConfigurationSource()
                }
                csrf { disable() }
                authorizeHttpRequests {
                    authorize(anyRequest, permitAll)
                }

                // Only configure OAuth2 if beans are available
                multiAccountOAuth2AuthorizedClientService?.let { oauth2Service ->
                    oauth2Login {
                        authorizedClientService = oauth2Service
                        customSuccessHandler?.let { handler ->
                            authenticationSuccessHandler = handler
                        }
                        customOidcUserService?.let { oidcService ->
                            userInfoEndpoint {
                                oidcUserService = oidcService
                            }
                        }
                    }
                }

                exceptionHandling {
                    authenticationEntryPoint = HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)
                }
            }
            return http.build()
        }

        @Bean
        fun corsConfigurationSource(): CorsConfigurationSource {
            val configuration = CorsConfiguration()
            configuration.allowedOriginPatterns = listOf("*")
            configuration.allowedMethods = listOf("*")
            configuration.allowedHeaders = listOf("*")
            configuration.allowCredentials = true

            val source = UrlBasedCorsConfigurationSource()
            source.registerCorsConfiguration("/**", configuration)
            return source
        }
    }
package com.matchalab.sublog_api.benchmark

import com.matchalab.sublog_api.security.config.CorsProperties
import com.matchalab.sublog_api.service.AppUserService
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@Profile(
    "benchmark"
)
@EnableConfigurationProperties(CorsProperties::class)
class BenchmarkSecurityConfig(
    private val appUserService: AppUserService
) {

    @Bean("benchmarkSecurityFilterChain")
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        println("DEBUG: BenchmarkSecurityConfig - Creating filter chain")
        http {
            securityMatcher("/api/benchmark/**", "/actuator/**")
            println("DEBUG: BenchmarkSecurityConfig - Security matcher set")
            addFilterBefore<UsernamePasswordAuthenticationFilter>(BenchmarkAuthFilter(appUserService))
            cors {
                configurationSource = corsConfigurationSource()
            }
            csrf { disable() }
            authorizeHttpRequests {
                // authorize("/api/benchmark/**", authenticated)
                authorize("/api/benchmark/**", permitAll)
                authorize("/actuator/**", permitAll)
                authorize(anyRequest, denyAll)
            }

            // Disable OAuth2 for benchmark - we use custom auth
            // multiAccountOAuth2AuthorizedClientService?.let { oauth2Service ->
            //     oauth2Login {
            //         authorizedClientService = oauth2Service
            //         customSuccessHandler?.let { handler ->
            //             authenticationSuccessHandler = handler
            //         }
            //         customOidcUserService?.let { oidcService ->
            //             userInfoEndpoint {
            //                 oidcUserService = oidcService
            //             }
            //         }
            //     }
            // }
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
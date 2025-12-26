// https://docs.spring.io/spring-security/reference/servlet/configuration/kotlin.html
// https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/index.html#publish-authentication-manager-bean
package com.matchalab.subscription_killer_api.config

import com.matchalab.subscription_killer_api.service.AppUserService
import com.matchalab.subscription_killer_api.service.MultiAccountOAuth2AuthorizedClientService
import com.matchalab.subscription_killer_api.service.TokenVerifierService
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.intercept.AuthorizationFilter
import org.springframework.web.cors.CorsConfigurationSource

@Configuration
@EnableWebSecurity(debug = true)
@EnableConfigurationProperties(CorsProperties::class)
open class WebSecurityConfig(
    private val corsConfigurationSource: CorsConfigurationSource,
    private val corsProperties: CorsProperties,
    private val authenticationConfiguration: AuthenticationConfiguration,
    private val googleIdTokenAuthenticationEntryPoint: GoogleIdTokenAuthenticationEntryPoint,
    private val multiAccountOAuth2AuthorizedClientService: MultiAccountOAuth2AuthorizedClientService,
    private val customSuccessHandler: CustomSuccessHandler
) {

    @Bean
    open fun filterChain(
        http: HttpSecurity,
        googleTokenAuthFilter: GoogleTokenAuthFilter,
        authRequestBodyValidationFilter: AuthRequestBodyValidationFilter,
    ): SecurityFilterChain {
        http {
            cors { configurationSource = corsConfigurationSource }
            csrf { disable() }
            authorizeHttpRequests {
                authorize(HttpMethod.OPTIONS, "/**", permitAll)
                authorize(HttpMethod.GET, "/ping", permitAll)
                authorize(HttpMethod.GET, "/login/**", permitAll)
                authorize(anyRequest, authenticated)
            }
            oauth2Login {
                authorizedClientService = multiAccountOAuth2AuthorizedClientService
                authenticationSuccessHandler = customSuccessHandler
//                authorizationEndpoint {
//                    authorizationRequestResolver = customAuthorizationRequestResolver
//                }
            }
            exceptionHandling { authenticationEntryPoint = googleIdTokenAuthenticationEntryPoint }

            addFilterBefore<AuthorizationFilter>(googleTokenAuthFilter)
            addFilterBefore<GoogleTokenAuthFilter>(authRequestBodyValidationFilter)
            // addFilterBefore<AuthorizationFilter>(authRequestBodyValidationFilter)
        }

        return http.build()
    }

    @Bean
    open fun authenticationManager(): AuthenticationManager {
        return authenticationConfiguration.authenticationManager
    }

    @Bean
    open fun googleIdTokenAuthenticationProvider(
        tokenVerifierService: TokenVerifierService,
        appUserService: AppUserService
    ): AuthenticationProvider {
        return GoogleIdTokenAuthenticationProvider(tokenVerifierService, appUserService)
    }
}

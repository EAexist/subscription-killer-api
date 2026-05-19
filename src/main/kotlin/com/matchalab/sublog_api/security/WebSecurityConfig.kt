package com.matchalab.sublog_api.security

import com.matchalab.sublog_api.security.config.AccountLinkingAuthorizedClientRepository
import com.matchalab.sublog_api.security.config.CorsProperties
import com.matchalab.sublog_api.security.config.CustomSuccessHandler
import com.matchalab.sublog_api.service.CustomOidcUserService
import com.matchalab.sublog_api.service.MultiAccountOAuth2AuthorizedClientService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.web.cors.CorsConfigurationSource

private val logger = KotlinLogging.logger {}

@Profile("!benchmark && !benchmark-dev")
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(CorsProperties::class)
open class WebSecurityConfig(
    private val corsConfigurationSource: CorsConfigurationSource,
    private val corsProperties: CorsProperties,
//    private val authenticationConfiguration: AuthenticationConfiguration,
//    private val googleIdTokenAuthenticationEntryPoint: GoogleIdTokenAuthenticationEntryPoint,
    private val multiAccountOAuth2AuthorizedClientService: MultiAccountOAuth2AuthorizedClientService,
    private val customSuccessHandler: CustomSuccessHandler,
    private val customOidcUserService: CustomOidcUserService,
    private val accountLinkingAuthorizedClientRepository: AccountLinkingAuthorizedClientRepository
) {

    @Bean
    open fun filterChain(
        http: HttpSecurity,
//        googleTokenAuthFilter: GoogleTokenAuthFilter,
//        authRequestBodyValidationFilter: AuthRequestBodyValidationFilter,
    ): SecurityFilterChain {
        http {
            cors { configurationSource = corsConfigurationSource }
            csrf { disable() }
            authorizeHttpRequests {
                authorize(HttpMethod.OPTIONS, "/**", permitAll)
                authorize(HttpMethod.GET, "/actuator/health", permitAll)
                authorize(HttpMethod.GET, "/ping", permitAll)
                authorize(HttpMethod.GET, "/oauth2/**", permitAll)
                authorize(HttpMethod.GET, "/login/**", permitAll)
                authorize(HttpMethod.GET, "/api/v1/guest/**", permitAll)
                authorize(anyRequest, authenticated)
            }
            oauth2Login {
                authorizedClientRepository = accountLinkingAuthorizedClientRepository
                authorizedClientService = multiAccountOAuth2AuthorizedClientService
                authenticationSuccessHandler = customSuccessHandler
                userInfoEndpoint {
                    oidcUserService = customOidcUserService
                }
                authenticationFailureHandler =
                    AuthenticationFailureHandler { _, response, exception ->
                        logger.error(exception) { "OAuth2 Error" }

                        response.sendRedirect("/login?error=${exception.message}")
                    }
//                authorizationEndpoint {
//                    authorizationRequestResolver = customAuthorizationRequestResolver
//                }
            }
            exceptionHandling {
                authenticationEntryPoint = HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)
            }
        }

        return http.build()
    }
}
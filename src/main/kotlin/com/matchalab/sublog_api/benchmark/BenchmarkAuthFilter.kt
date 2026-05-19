package com.matchalab.sublog_api.benchmark

import com.matchalab.sublog_api.core.dto.AddGoogleAccountCommand
import com.matchalab.sublog_api.domain.AppUser
import com.matchalab.sublog_api.security.CustomOidcUser
import com.matchalab.sublog_api.service.AppUserService
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.OidcUserInfo
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant
import java.util.*

private val logger = KotlinLogging.logger {}

class BenchmarkAuthFilter(private val appUserService: AppUserService) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
//        logger.debug { "DEBUG: BenchmarkAuthFilter - Processing request: ${request.method} ${request.requestURI}" }

        // Skip filter for actuator endpoints
        if (request.requestURI.startsWith("/actuator/")) {
            filterChain.doFilter(request, response)
            return
        }

        val userHeader = request.getHeader("X-Benchmark-User-Id")

        if (userHeader != null) {
            val appUserId = UUID.fromString(userHeader)
            val appUser =
                AppUser(
                    name = "Benchmark User",
                )
            appUserService.addGoogleAccount(
                appUser,
                AddGoogleAccountCommand(
                    appUserId.toString(),
                    "Benchmark User",
                    "${appUserId.toString().take(8)}@example.com",
                    "mock-refresh-token",
                    "mock-access-token",
                    Instant.now().plusSeconds(3600),
                )
            )
            appUserService.save(
                appUser
            )

            // Create a minimal mock OidcIdToken
            val oidcIdToken = OidcIdToken.withTokenValue("mock-token")
                .subject("benchmark-user")
                .issuer("https://accounts.google.com")
                .audience(listOf("benchmark-client"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("email", "benchmark@example.com")
                .build()

            // Create a minimal mock OidcUserInfo
            val oidcUserInfo = OidcUserInfo.builder()
                .subject("benchmark-user")
                .email("benchmark@example.com")
                .build()

            // Create a DefaultOidcUser
            val oidcUser = DefaultOidcUser(
                listOf(SimpleGrantedAuthority("ROLE_USER")),
                oidcIdToken,
                oidcUserInfo
            )

            // Create CustomOidcUser with the user ID
            val customOidcUser = CustomOidcUser(
                appUserId = appUser.id,
                authoritiesInternal = listOf(SimpleGrantedAuthority("ROLE_USER")),
                oidcUser = oidcUser
            )

            val auth = UsernamePasswordAuthenticationToken(
                customOidcUser, // This becomes the 'principal'
                null,
                customOidcUser.authorities
            )

            SecurityContextHolder.getContext().authentication = auth
            logger.debug { "DEBUG: BenchmarkAuthFilter - Set authentication: ${auth.principal}" }
        } else {
            logger.debug { "DEBUG: BenchmarkAuthFilter - No X-Benchmark-User-Id header found" }
        }

        filterChain.doFilter(request, response)
    }
}
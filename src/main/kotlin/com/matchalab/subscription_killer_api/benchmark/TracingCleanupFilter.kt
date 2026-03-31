package com.matchalab.subscription_killer_api.benchmark

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import io.opentelemetry.context.Context
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Profile
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/*
* Prevent Trace Context Leakage
* */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Profile("benchmark")
class ObservationCleanupFilter(
    private val observationRegistry: ObservationRegistry
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val root = Context.root()
        val scope = root.makeCurrent()
        try {
            filterChain.doFilter(request, response)
        } finally {
            scope.close()
        }
    }
}
package com.matchalab.sublog_api.benchmark

import io.micrometer.observation.Observation
import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.api.trace.Span
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Profile
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter


@Component
@Profile("benchmark")
@Order(Ordered.HIGHEST_PRECEDENCE)
class GlobalBenchmarkTracingFilter(
    private val manager: BenchmarkTraceManager,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return path.contains("/benchmark/start") || path.contains("/benchmark/stop")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val index = request.getHeader("X-Benchmark-Index")
        val globalParent = manager.globalParentObservation

        val baggage = Baggage.current().toBuilder()
            .put("benchmark.request.id", index)
            .build()

        val scope = baggage.makeCurrent()

        (globalParent?.openScope() ?: Observation.Scope.NOOP).use {
            Span.current().setAttribute("benchmark.request.id", index)
            filterChain.doFilter(request, response)
        }
    }
}
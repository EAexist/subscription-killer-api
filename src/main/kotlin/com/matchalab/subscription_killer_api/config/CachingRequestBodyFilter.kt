package com.matchalab.subscription_killer_api.config

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper

private val logger = KotlinLogging.logger {}

// @Component
class CachingRequestBodyFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
            request: HttpServletRequest,
            response: HttpServletResponse,
            filterChain: FilterChain
    ) {
        val cachedRequest = ContentCachingRequestWrapper(request)

        // val bodyBytes = cachedRequest.inputStream.readAllBytes()
        // val bodyString = String(bodyBytes, Charsets.UTF_8)
        // logger.debug("Body Size=${bodyBytes.size}, Body=$bodyString")

        filterChain.doFilter(cachedRequest, response)
    }
}

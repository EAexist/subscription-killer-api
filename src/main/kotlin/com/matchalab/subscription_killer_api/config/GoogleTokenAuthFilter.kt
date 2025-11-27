package com.matchalab.subscription_killer_api.config

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse

class GoogleTokenAuthFilter : Filter {

    override fun doFilter(
            servletRequest: ServletRequest,
            servletResponse: ServletResponse,
            filterChain: FilterChain,
    ) {}
}

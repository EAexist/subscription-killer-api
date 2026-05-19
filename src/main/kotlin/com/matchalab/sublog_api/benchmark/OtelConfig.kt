package com.matchalab.sublog_api.benchmark

import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("benchmark")
class OtelConfig {

    @Bean
    fun requestIdSpanProcessor(): SpanProcessor {
        return object : SpanProcessor {

            override fun onStart(parentContext: Context, span: ReadWriteSpan) {
                val requestId = Baggage.fromContext(parentContext)
                    .getEntryValue("benchmark.request.id")

                if (requestId != null) {
                    span.setAttribute("benchmark.request.id", requestId)
                }
            }

            override fun isStartRequired(): Boolean = true
            override fun isEndRequired(): Boolean = false
            override fun onEnd(span: ReadableSpan) {}
        }
    }
}
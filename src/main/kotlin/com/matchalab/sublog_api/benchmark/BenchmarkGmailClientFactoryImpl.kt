package com.matchalab.sublog_api.benchmark

import com.matchalab.sublog_api.subscription.service.gmailclientadapter.GmailClientAdapter
import com.matchalab.sublog_api.subscription.service.gmailclientfactory.DefaultGmailClientFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Profile("benchmark")
@Service
@Primary
class BenchmarkGmailClientFactoryImpl(
    val benchmarkMockGmailClientAdapter: BenchmarkMockGmailClientAdapter
) : DefaultGmailClientFactory {
    override fun createAdapter(subject: String): GmailClientAdapter {
        return benchmarkMockGmailClientAdapter
    }
}
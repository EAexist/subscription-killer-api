package com.matchalab.sublog_api.subscription.service.gmailclientfactory

import com.matchalab.sublog_api.datasets.EmailDatasetProvider
import com.matchalab.sublog_api.subscription.service.gmailclientadapter.GmailClientAdapter
import com.matchalab.sublog_api.subscription.service.gmailclientadapter.MockGmailClientAdapter
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("!gmail")
class MockGmailClientFactory(
    private val emailDatasetProvider: EmailDatasetProvider
) : DefaultGmailClientFactory {
    override fun createAdapter(subject: String): GmailClientAdapter {
        return MockGmailClientAdapter(emailDatasetProvider)
    }
}
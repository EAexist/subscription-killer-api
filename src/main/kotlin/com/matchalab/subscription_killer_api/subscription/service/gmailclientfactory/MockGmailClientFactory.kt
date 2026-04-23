package com.matchalab.subscription_killer_api.subscription.service.gmailclientfactory

import com.matchalab.subscription_killer_api.datasets.EmailDatasetProvider
import com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter.GmailClientAdapter
import com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter.MockGmailClientAdapter
import org.springframework.stereotype.Service

@Service
class MockGmailClientFactory(
    private val emailDatasetProvider: EmailDatasetProvider
) : DefaultGmailClientFactory {
    override fun createAdapter(subject: String): GmailClientAdapter {
        return MockGmailClientAdapter(emailDatasetProvider)
    }
}
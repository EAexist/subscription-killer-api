package com.matchalab.subscription_killer_api.subscription.service.gmailclientfactory

import com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter.GmailClientAdapter
import com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter.MockGmailClientAdapter
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Profile("!google-auth || !gmail")
@Service
class MockGmailClientFactory(
    val mockGmailClientAdapter: MockGmailClientAdapter
) : GmailClientFactory {
    override fun createAdapter(subject: String): GmailClientAdapter {
        return mockGmailClientAdapter
    }
}
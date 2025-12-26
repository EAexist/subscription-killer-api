package com.matchalab.subscription_killer_api.subscription.service.gmailclientfactory

import com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter.GmailClientAdapter
import com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter.MockGmailClientAdapter
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Profile("!prod && !gmail")
@Service
class MockGmailClientFactory(
) : GmailClientFactory {
    override fun createAdapter(credentialsIdentifier: String): GmailClientAdapter {
        return MockGmailClientAdapter()
    }
}
package com.matchalab.subscription_killer_api.subscription.service.gmailclientfactory

import com.matchalab.subscription_killer_api.config.GuestAppUserProperties
import com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter.GmailClientAdapter
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Profile("google-auth && gmail")
@Service
@Primary
class ProxyGmailClientFactory(
    private val gmailClientFactoryImpl: GmailClientFactoryImpl,
    private val mockGmailClientFactory: MockGmailClientFactory,
    private val guestAppUserProperties: GuestAppUserProperties,
) : GmailClientFactory {
    override fun createAdapter(subject: String): GmailClientAdapter {
        return if (subject == guestAppUserProperties.subject) {
            mockGmailClientFactory.createAdapter("")
        } else {
            gmailClientFactoryImpl.createAdapter(subject)
        }
    }
}
package com.matchalab.subscription_killer_api.subscription.service.gmailclientfactory

import com.matchalab.subscription_killer_api.config.GuestAppUserProperties
import com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter.GmailClientAdapter
import com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter.ObservingGmailClientAdapter
import io.micrometer.observation.ObservationRegistry
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service

@Service
class ProxyGmailClientFactory(
    private val gmailClientFactory: DefaultGmailClientFactory,
    private val mockGmailClientFactory: MockGmailClientFactory,
    private val guestAppUserProperties: GuestAppUserProperties,
    private val observationRegistry: ObservationRegistry
) : GmailClientFactory {
    override fun createAdapter(subject: String): GmailClientAdapter {
        return if (subject in guestAppUserProperties.subjects) {
            ObservingGmailClientAdapter(mockGmailClientFactory.createAdapter(subject), observationRegistry)

        } else {
            ObservingGmailClientAdapter(gmailClientFactory.createAdapter(subject), observationRegistry)
        }
    }
}
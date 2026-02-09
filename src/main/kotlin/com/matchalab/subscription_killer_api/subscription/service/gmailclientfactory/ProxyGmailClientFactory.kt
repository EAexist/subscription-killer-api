package com.matchalab.subscription_killer_api.subscription.service.gmailclientfactory

import com.matchalab.subscription_killer_api.config.GuestAppUserProperties
import com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter.GmailClientAdapter
import com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter.ObservingGmailClientAdapter
import io.micrometer.observation.ObservationRegistry
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Profile("google-auth && gmail")
@Service
@Primary
class ProxyGmailClientFactory(
    private val gmailClientFactoryImpl: AbstractGmailClientFactoryImpl,
    private val mockGmailClientFactory: MockGmailClientFactory,
    private val guestAppUserProperties: GuestAppUserProperties,
    private val observationRegistry: ObservationRegistry
) : GmailClientFactory {
    override fun createAdapter(subject: String): GmailClientAdapter {
        return if (subject == guestAppUserProperties.subject) {
            ObservingGmailClientAdapter(mockGmailClientFactory.createAdapter(""), observationRegistry)

        } else {
            ObservingGmailClientAdapter(gmailClientFactoryImpl.createAdapter(subject), observationRegistry)
        }
    }
}
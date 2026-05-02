package com.matchalab.subscription_killer_api.subscription.service.gmailclientfactory

import com.matchalab.subscription_killer_api.guest.EmptyGmailClientAdapter
import com.matchalab.subscription_killer_api.guest.GuestAppUserGmailClientFactory
import com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter.GmailClientAdapter
import com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter.ObservingGmailClientAdapter
import io.micrometer.observation.ObservationRegistry
import org.springframework.stereotype.Service

@Service
class ProxyGmailClientFactory(
    private val gmailClientFactory: DefaultGmailClientFactory,
    private val guestAppUserGmailClientFactory: GuestAppUserGmailClientFactory,
    private val observationRegistry: ObservationRegistry
) : GmailClientFactory {
    override fun createAdapter(subject: String): GmailClientAdapter {
        if (subject.startsWith("GUEST")) {
            if (subject.startsWith("GUEST-0"))
                return ObservingGmailClientAdapter(
                    guestAppUserGmailClientFactory.createAdapter(subject),
                    observationRegistry
                )
            else {
                return EmptyGmailClientAdapter()
            }
        } else {
            return ObservingGmailClientAdapter(
                gmailClientFactory.createAdapter(subject),
                observationRegistry
            )
        }
    }
}
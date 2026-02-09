package com.matchalab.subscription_killer_api.benchmark

import com.matchalab.subscription_killer_api.config.GoogleClientProperties
import com.matchalab.subscription_killer_api.domain.GoogleAccount
import com.matchalab.subscription_killer_api.repository.GoogleAccountRepository
import com.matchalab.subscription_killer_api.subscription.config.MailProperties
import com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter.GmailClientAdapter
import com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter.GmailClientAdapterImpl
import com.matchalab.subscription_killer_api.subscription.service.gmailclientfactory.AbstractGmailClientFactoryImpl
import io.micrometer.observation.ObservationRegistry
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Profile("benchmark")
@Service
class NonCachingGmailClientFactoryImpl(
    googleAccountRepository: GoogleAccountRepository,
    googleClientProperties: GoogleClientProperties,
    mailProperties: MailProperties,
    observationRegistry: ObservationRegistry,
) : AbstractGmailClientFactoryImpl(
    googleAccountRepository,
    googleClientProperties,
    mailProperties,
    observationRegistry
) {

    override fun isTokenExpiringSoon(account: GoogleAccount): Boolean {
        return true
    }

    override fun createAdapter(subject: String): GmailClientAdapter {
        val authenticatedGmailClient = createClient(subject)
        return GmailClientAdapterImpl(authenticatedGmailClient, mailProperties, observationRegistry)
    }
}

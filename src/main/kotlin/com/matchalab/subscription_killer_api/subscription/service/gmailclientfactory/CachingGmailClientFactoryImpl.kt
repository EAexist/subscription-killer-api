package com.matchalab.subscription_killer_api.subscription.service.gmailclientfactory

import com.matchalab.subscription_killer_api.config.GoogleClientProperties
import com.matchalab.subscription_killer_api.domain.GoogleAccount
import com.matchalab.subscription_killer_api.repository.GoogleAccountRepository
import com.matchalab.subscription_killer_api.subscription.config.MailProperties
import com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter.GmailClientAdapter
import io.micrometer.observation.ObservationRegistry
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Profile("google-auth && gmail")
@Service
class CachingGmailClientFactoryImpl(
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

    private val adapterCache = ConcurrentHashMap<String, GmailClientAdapter>()

    override fun isTokenExpiringSoon(account: GoogleAccount): Boolean {
        return account.expiresAt?.isBefore(
            Instant.now().plusSeconds(googleClientProperties.tokenRefreshThresholdSeconds)
        )
            ?: true
    }

    override fun createAdapter(subject: String): GmailClientAdapter {
        return adapterCache.getOrPut(subject) {
            val authenticatedGmailClient = createClient(subject)
            com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter.GmailClientAdapterImpl(
                authenticatedGmailClient,
                mailProperties,
                observationRegistry
            )
        }
    }

    fun clearCache() {
        return adapterCache.clear()
    }
}

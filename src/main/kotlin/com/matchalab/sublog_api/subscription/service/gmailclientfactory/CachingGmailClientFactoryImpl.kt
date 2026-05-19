package com.matchalab.sublog_api.subscription.service.gmailclientfactory

import com.matchalab.sublog_api.config.GoogleClientProperties
import com.matchalab.sublog_api.domain.GoogleAccount
import com.matchalab.sublog_api.repository.GoogleAccountRepository
import com.matchalab.sublog_api.subscription.config.MailProperties
import com.matchalab.sublog_api.subscription.service.gmailclientadapter.GmailClientAdapter
import com.matchalab.sublog_api.subscription.service.gmailclientadapter.GmailClientAdapterImpl
import io.micrometer.observation.ObservationRegistry
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Profile("oauth && gmail")
@Primary
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
            GmailClientAdapterImpl(
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

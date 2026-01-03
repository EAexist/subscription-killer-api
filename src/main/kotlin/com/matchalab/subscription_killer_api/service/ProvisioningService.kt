package com.matchalab.subscription_killer_api.service

import com.matchalab.subscription_killer_api.subscription.service.gmailclientfactory.GmailClientFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.util.*

private val logger = KotlinLogging.logger {}

@Service
class ProvisioningService(
    private val appUserService: AppUserService,
    private val gmailClientFactory: GmailClientFactory
) {
    fun provisionResources(appUserId: UUID) {
        appUserService.findGoogleAccountSubjectsByAppUserId(appUserId).forEach { subject ->
            gmailClientFactory.createAdapter(subject)
        }
    }
}

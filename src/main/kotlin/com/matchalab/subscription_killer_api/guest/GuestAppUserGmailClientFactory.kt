package com.matchalab.subscription_killer_api.guest

import com.fasterxml.jackson.databind.ObjectMapper
import com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter.GmailClientAdapter
import com.matchalab.subscription_killer_api.subscription.service.gmailclientfactory.GmailClientFactory
import org.springframework.core.io.support.ResourcePatternResolver
import org.springframework.stereotype.Service

@Service
class GuestAppUserGmailClientFactory(
    private val objectMapper: ObjectMapper,
    private val resourcePatternResolver: ResourcePatternResolver
) : GmailClientFactory {
    override fun createAdapter(subject: String): GmailClientAdapter {
        return GuestAppUserGmailClientAdapter(objectMapper, resourcePatternResolver)
    }
}
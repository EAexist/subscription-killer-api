package com.matchalab.subscription_killer_api.subscription.service.gmailclientfactory

import com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter.GmailClientAdapter

interface GmailClientFactory {
    fun createAdapter(credentialsIdentifier: String): GmailClientAdapter
}
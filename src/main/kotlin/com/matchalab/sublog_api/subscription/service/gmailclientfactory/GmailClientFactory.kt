package com.matchalab.sublog_api.subscription.service.gmailclientfactory

import com.matchalab.sublog_api.subscription.service.gmailclientadapter.GmailClientAdapter

interface GmailClientFactory {
    fun createAdapter(subject: String): GmailClientAdapter
}
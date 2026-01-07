package com.matchalab.subscription_killer_api.config

import com.matchalab.subscription_killer_api.domain.LocaleType
import com.matchalab.subscription_killer_api.repository.ServiceProviderRepository
import com.matchalab.subscription_killer_api.subscription.EmailDetectionRule
import com.matchalab.subscription_killer_api.subscription.EmailSource
import com.matchalab.subscription_killer_api.subscription.ServiceProvider
import java.util.*

open class TestDataFactory(
    private val serviceProviderRepository: ServiceProviderRepository
) {
    data class ServiceProviderJson(
        val aliasNames: Map<String, String>,
        val emailAddresses: List<String>
    )

    fun createServiceProvider(
        displayName: String,
        emailSources: MutableList<EmailSource>?,
    ) =
        ServiceProvider(
            UUID.randomUUID(),
            displayName,
            "$displayName.com",
            "www.$displayName.com",
            mutableMapOf(LocaleType.EN.name to displayName),
            emailSources ?: mutableListOf<EmailSource>()
        )

    fun createEmailSource(
        targetAddress: String,
        eventRules: MutableList<EmailDetectionRule> = mutableListOf()
    ) =
        EmailSource(null, targetAddress, eventRules)

}
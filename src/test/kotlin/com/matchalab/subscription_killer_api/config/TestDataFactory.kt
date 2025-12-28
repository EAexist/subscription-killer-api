package com.matchalab.subscription_killer_api.config

import com.matchalab.subscription_killer_api.domain.LocaleType
import com.matchalab.subscription_killer_api.repository.ServiceProviderRepository
import com.matchalab.subscription_killer_api.subscription.EmailDetectionRule
import com.matchalab.subscription_killer_api.subscription.EmailSource
import com.matchalab.subscription_killer_api.subscription.ServiceProvider
import com.matchalab.subscription_killer_api.subscription.SubscriptionEventType
import com.matchalab.subscription_killer_api.utils.readJsonList
import org.springframework.core.io.ClassPathResource
import org.springframework.transaction.annotation.Transactional
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
            mutableMapOf(LocaleType.EN.name to displayName),
            emailSources ?: mutableListOf<EmailSource>()
        )

    fun createEmailSource(
        targetAddress: String,
        eventRules: MutableMap<SubscriptionEventType, EmailDetectionRule>?
    ) =
        EmailSource(null, targetAddress, (eventRules ?: mutableMapOf()))

    fun createServiceProvidersFromJson(jsonPath: String = "static/service-provider.json"): List<ServiceProvider> {
        val dtos: List<ServiceProviderJson> = readJsonList(ClassPathResource(jsonPath).inputStream)

        return dtos.map { dto ->
            val provider = ServiceProvider(
                displayName = dto.aliasNames["EN"] ?: "Unknown",
                aliasNames = dto.aliasNames.toMutableMap()
            )
            val sources = dto.emailAddresses.map {
                EmailSource(targetAddress = it, serviceProvider = provider)
            }
            provider.emailSources.addAll(sources)
            provider
        }
    }

    @Transactional
    open fun persistSampleData(path: String = "static/service-provider.json"): List<ServiceProvider> {
        return serviceProviderRepository.saveAll(createServiceProvidersFromJson(path))
    }
}
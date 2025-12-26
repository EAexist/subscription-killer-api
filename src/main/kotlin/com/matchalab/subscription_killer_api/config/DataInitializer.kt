package com.matchalab.subscription_killer_api.config

import com.matchalab.subscription_killer_api.repository.ServiceProviderRepository
import com.matchalab.subscription_killer_api.subscription.EmailSource
import com.matchalab.subscription_killer_api.subscription.ServiceProvider
import com.matchalab.subscription_killer_api.utils.readJsonList
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional


@Component
class DataInitializer(
    private val serviceProviderRepository: ServiceProviderRepository
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
    }

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

    data class ServiceProviderJson(
        val aliasNames: Map<String, String>,
        val emailAddresses: List<String>
    )

}
package com.matchalab.subscription_killer_api.config

import com.matchalab.subscription_killer_api.repository.ServiceProviderRepository
import com.matchalab.subscription_killer_api.subscription.EmailSource
import com.matchalab.subscription_killer_api.subscription.ServiceProvider
import com.matchalab.subscription_killer_api.utils.readJsonList
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

private val logger = KotlinLogging.logger {}

@Component
class DataInitializer(
    private val transactionTemplate: TransactionTemplate,
    private val serviceProviderRepository: ServiceProviderRepository
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        transactionTemplate.execute {
            persistSampleData()
        }
    }

    open fun persistSampleData() {
        val providers = createServiceProvidersFromJson()
        val existingNames = serviceProviderRepository.findAll().map { it.displayName }.toSet()

        val newProviders = providers.filter { it.displayName !in existingNames }

        if (newProviders.isNotEmpty()) {
            serviceProviderRepository.saveAll(newProviders)
        }
//        serviceProviderRepository.findAll().forEach { sp ->
//            logger.debug { "\uD83D\uDC1E provider.emailSources: ${sp.emailSources.joinToString(", ") { it.targetAddress }}" }
//        }
    }

    fun createServiceProvidersFromJson(jsonPath: String = "static/service-provider.json"): List<ServiceProvider> {
        val dtos: List<ServiceProviderJson> = readJsonList(ClassPathResource(jsonPath).inputStream)

        return dtos.map { dto ->
            val provider = ServiceProvider(
                displayName = dto.aliasNames["EN"] ?: "Unknown",
                logoDevSuffix = dto.logoDevSuffix,
                websiteUrl = dto.websiteUrl,
                aliasNames = dto.aliasNames.toMutableMap()
            )
            val sources = dto.emailAddresses.map {
                EmailSource(targetAddress = it, serviceProvider = provider)
            }
            provider.emailSources.addAll(sources)

            provider
        }
    }


    data class ServiceProviderJson(
        val aliasNames: Map<String, String>,
        val logoDevSuffix: String,
        val websiteUrl: String,
        val emailAddresses: List<String>
    )

}
package com.matchalab.subscription_killer_api.config

import com.matchalab.subscription_killer_api.domain.AppUser
import com.matchalab.subscription_killer_api.domain.GoogleAccount
import com.matchalab.subscription_killer_api.repository.AppUserRepository
import com.matchalab.subscription_killer_api.repository.ServiceProviderRepository
import com.matchalab.subscription_killer_api.subscription.EmailSource
import com.matchalab.subscription_killer_api.subscription.ServiceProvider
import com.matchalab.subscription_killer_api.utils.readJsonList
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

private val logger = KotlinLogging.logger {}

@Component
@Profile("dev")
class DataInitializer(
    private val transactionTemplate: TransactionTemplate,
    private val serviceProviderRepository: ServiceProviderRepository,
    private val appUserRepository: AppUserRepository,
    private val guestAppUserProperties: GuestAppUserProperties,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        transactionTemplate.execute {
            persistSampleData()
            persistGuestAppUser()
        }
    }

    open fun persistGuestAppUser() {
        if (!appUserRepository.existsByGoogleAccounts_Subject(guestAppUserProperties.subject)) {
            val guestGoogleAccount =
                GoogleAccount(
                    guestAppUserProperties.subject,
                    guestAppUserProperties.name,
                    guestAppUserProperties.email
                )

            val guestAppUser = AppUser(
                name = guestAppUserProperties.name
            )

            guestAppUser.addGoogleAccount(guestGoogleAccount)
            appUserRepository.save(guestAppUser)
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
//            logger.debug { "🔊 | provider.emailSources: ${sp.emailSources.joinToString(", ") { it.targetAddress }}" }
//        }
    }

    fun createServiceProvidersFromJson(jsonPath: String = "static/service-providers.json"): List<ServiceProvider> {
        val dtos: List<ServiceProviderJson> = readJsonList(ClassPathResource(jsonPath).inputStream)

        return dtos.map { dto ->
            val provider = ServiceProvider(
                displayName = dto.aliasNames["KR"] ?: dto.aliasNames["EN"] ?: "Unknown",
                logoDevSuffix = dto.logoDevSuffix,
                websiteUrl = dto.websiteUrl,
                subscriptionPageUrl = dto.subscriptionPageUrl,
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
        val subscriptionPageUrl: String,
        val emailAddresses: List<String>
    )

}
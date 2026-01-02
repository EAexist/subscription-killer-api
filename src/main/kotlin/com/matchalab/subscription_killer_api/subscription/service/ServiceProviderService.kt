package com.matchalab.subscription_killer_api.subscription.service

import com.matchalab.subscription_killer_api.repository.EmailSourceRepository
import com.matchalab.subscription_killer_api.repository.ServiceProviderRepository
import com.matchalab.subscription_killer_api.repository.SubscriptionRepository
import com.matchalab.subscription_killer_api.subscription.EmailSource
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.subscription.ServiceProvider
import com.matchalab.subscription_killer_api.subscription.dto.ServiceProviderResponseDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

private val logger = KotlinLogging.logger {}

@Service
class ServiceProviderService(
    private val serviceProviderRepository: ServiceProviderRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val emailSourceRepository: EmailSourceRepository,
    private val emailDetectionRuleService: EmailDetectionRuleService,
) {
    interface ServiceProviderProjection {
        val id: UUID
        val displayName: String
        val canAnalyzePayment: Boolean
    }

    val maxNumberOfEmailDetectionRuleAnalysis: Long = 40

    fun findByIdWithSubscriptions(id: UUID): ServiceProvider? {
        return serviceProviderRepository.findByIdWithSubscriptions(id)
    }

    fun findAllWithEmailSources(): List<ServiceProvider> {
        return serviceProviderRepository.findAllWithEmailSources()
    }

    fun findAllWithAliases(): List<ServiceProvider> {
        return serviceProviderRepository.findAllWithAliases()
    }

    @Transactional(readOnly = true)
    fun findAllWithEmailSourcesAndAliases(): List<ServiceProvider> {
        findAllWithEmailSources()
        return findAllWithAliases()
    }

    fun save(serviceProvider: ServiceProvider): ServiceProvider {
        return serviceProviderRepository.save(serviceProvider)
    }

    fun saveAll(serviceProviders: List<ServiceProvider>): List<ServiceProvider> {
        return serviceProviderRepository.saveAll(serviceProviders)
    }

    fun findByActiveEmailAddressesInWithEmailSources(addresses: List<String>): List<ServiceProvider> {
        return serviceProviderRepository.findByActiveEmailAddressesInWithEmailSources(addresses)
    }

    fun findDtoById(id: UUID): ServiceProviderResponseDto? {
        return serviceProviderRepository.findWithEmailSourceById(id)?.let {
            ServiceProviderResponseDto(
                id = it.id!!,
                displayName = it.displayName,
                logoDevSuffix = it.logoDevSuffix,
                canAnalyzePayment = it.isEmailDetectionRuleAvailable()
            )
        }
    }

    fun findAllDtoById(ids: List<UUID>): List<ServiceProviderResponseDto> {
        return serviceProviderRepository.findWithEmailSourceAllByIdIn(ids).map {
            ServiceProviderResponseDto(
                id = it.id!!,
                displayName = it.displayName,
                logoDevSuffix = it.logoDevSuffix,
                canAnalyzePayment = it.isEmailDetectionRuleAvailable()
            )
        }
    }

    fun addEmailSourcesFromMessages(messages: List<GmailMessage>): List<ServiceProvider> {

        logger.debug { "[addEmailSourcesFromMessages] messages.size: ${messages.size}" }

        data class GmailSenderDto(
            val name: String?,
            val email: String,
        ) {}

        val namedSenders: List<GmailSenderDto> =
            messages.filter { it.senderName != null }.map { GmailSenderDto(it.senderName, it.senderEmail) }.distinct()

        val addressesInMessages = namedSenders.map { it.email }.distinct()
        val existingAddresses = emailSourceRepository.findExistingAddresses(addressesInMessages)
        val newSenders = namedSenders.filter { !existingAddresses.contains(it.email) }

        logger.debug { "\uD83D\uDC1E [addEmailSourcesFromMessages] addressesInMessages: $addressesInMessages" }
        logger.debug { "\uD83D\uDC1E [addEmailSourcesFromMessages] existingAddresses: $existingAddresses" }
        logger.debug { "\uD83D\uDC1E [addEmailSourcesFromMessages] newSenders: $newSenders" }


        val aliasNameToNewEmails = newSenders.groupBy(
            { it.name!! },
            { it.email }
        )
        val updatedProviders = mutableListOf<ServiceProvider>()

        aliasNameToNewEmails.forEach { (aliasName, emails) ->
            //@TODO Optimize
            val emailOwningServiceProviders = serviceProviderRepository.findByAliasNameWithEmailSources(aliasName)
            emailOwningServiceProviders?.let { serviceProvider ->
                serviceProvider.addAllEmailSources(emails.map { EmailSource(null, it) })
                updatedProviders.add(serviceProvider)
            }
        }
        if (updatedProviders.isEmpty()) {
            return emptyList()
        }
        return saveAll(updatedProviders)
    }

    fun updateEmailDetectionRules(
        provider: ServiceProvider,
        addressToMessages: Map<String, List<GmailMessage>>
    ): ServiceProvider {

        logger.debug { "\uD83D\uDE80 [updateEmailDetectionRules]" }

        val isEmailDetectionRuleAnalysisAvailable =
            subscriptionRepository.countByServiceProviderId(provider.requiredId) < maxNumberOfEmailDetectionRuleAnalysis

        if ((!provider.isEmailDetectionRuleComplete()) && isEmailDetectionRuleAnalysisAvailable) {
            provider.emailSources.forEach { emailSource ->
                val messages: List<GmailMessage> = addressToMessages[emailSource.targetAddress] ?: emptyList()
                if (messages.isNotEmpty()) {
                    val updatedEmailDetectionRules = emailDetectionRuleService.updateRules(emailSource, messages)
                    emailSource.updateEmailDetectionRules(
                        updatedEmailDetectionRules
                    )
                }
            }
        }

        //@TODO If Rule is Complete, flag unused emailSources as disabled
        if (provider.isEmailDetectionRuleComplete()) {
            provider.emailSources.forEach {
                if (it.eventRules.isEmpty()) {
                    it.isActive = false
                }
            }
        }

        return save(provider)
    }

//    fun findByIdOrNull(id: UUID): ServiceProvider? {
//        return serviceProviderRepository.findByIdOrNull(id)
//    }
//
//    fun findAll(): List<ServiceProvider> {
//        return serviceProviderRepository.findAll()
//    }
//
//    fun fetchWithEmailSources(providers: List<ServiceProvider>): List<ServiceProvider> {
//        return serviceProviderRepository.fetchWithEmailSources(providers)
//    }
}
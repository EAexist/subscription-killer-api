package com.matchalab.subscription_killer_api.subscription.service

import com.matchalab.subscription_killer_api.repository.EmailSourceRepository
import com.matchalab.subscription_killer_api.repository.ServiceProviderRepository
import com.matchalab.subscription_killer_api.repository.SubscriptionRepository
import com.matchalab.subscription_killer_api.subscription.EmailSource
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.utils.toDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class ServiceProviderService(
    private val serviceProviderRepository: ServiceProviderRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val emailSourceRepository: EmailSourceRepository,
    private val emailDetectionRuleService: EmailDetectionRuleService,
) {
    val maxNumberOfEmailDetectionRuleAnalysis: Long = 40

    fun findAll(): List<ServiceProvider> {
        return serviceProviderRepository.findAll()
    }

    fun save(serviceProvider: ServiceProvider): ServiceProvider {
        return serviceProviderRepository.save(serviceProvider)
    }

    fun saveAll(serviceProviders: List<ServiceProvider>): List<ServiceProvider> {
        return serviceProviderRepository.saveAll(serviceProviders)
    }

    fun findByActiveEmailAddressesIn(addresses: List<String>): List<ServiceProvider> {
        return serviceProviderRepository.findByActiveEmailAddressesIn(addresses)
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

        val aliasNameToNewEmails = newSenders.groupBy(
            { it.name!! },
            { it.email }
        )
        val updatedProviders = mutableListOf<ServiceProvider>()

        aliasNameToNewEmails.forEach { (aliasName, emails) ->
            //@TODO Optimize
            val emailOwningServiceProvider = serviceProviderRepository.findByAliasName(aliasName)
            emailOwningServiceProvider?.let { serviceProvider ->
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

        logger.debug { "[updateEmailDetectionRules]" }

        val isEmailDetectionRuleAnalysisAvailable =
            provider.id?.let { subscriptionRepository.countByServiceProviderId(it) < maxNumberOfEmailDetectionRuleAnalysis }
                ?: false

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

        logger.info { "[updateEmailDetectionRules] provider: ${provider.toDto()}" }

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

}
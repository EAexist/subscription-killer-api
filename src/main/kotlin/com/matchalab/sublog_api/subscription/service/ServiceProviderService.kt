package com.matchalab.sublog_api.subscription.service

import com.matchalab.sublog_api.repository.EmailSourceRepository
import com.matchalab.sublog_api.repository.ServiceProviderRepository
import com.matchalab.sublog_api.subscription.EmailSource
import com.matchalab.sublog_api.subscription.GmailMessage
import com.matchalab.sublog_api.subscription.ServiceProvider
import com.matchalab.sublog_api.subscription.dto.ServiceProviderResponseDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.*

private val logger = KotlinLogging.logger {}

@Service
class ServiceProviderService(
    private val serviceProviderRepository: ServiceProviderRepository,
    private val emailSourceRepository: EmailSourceRepository,
) {

    fun findByIdOrNotFound(id: UUID): ServiceProvider {
        return serviceProviderRepository.findByIdOrNull(id) ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "ServiceProvider not found"
        )
    }

    fun findByIdWithSubscriptions(id: UUID): ServiceProvider {
        return serviceProviderRepository.findByIdWithSubscriptions(id) ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "ServiceProvider not found"
        )
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

    fun getReferenceById(serviceProviderId: UUID): ServiceProvider {
        return serviceProviderRepository.getReferenceById(serviceProviderId)
    }

    fun findDtoById(id: UUID): ServiceProviderResponseDto? {
        return serviceProviderRepository.findWithEmailSourceById(id)?.let {
            ServiceProviderResponseDto(
                id = it.id!!,
                displayName = it.displayName,
                logoDevSuffix = it.logoDevSuffix,
                websiteUrl = it.websiteUrl,
                subscriptionPageUrl = it.subscriptionPageUrl,
                canAnalyzeSubscription = it.isSubscriptionEventRuleAvailable()
            )
        }
    }

    fun findAllDtoById(ids: List<UUID>): List<ServiceProviderResponseDto> {
        return serviceProviderRepository.findWithEmailSourceAllByIdIn(ids).map {
            ServiceProviderResponseDto(
                id = it.id!!,
                displayName = it.displayName,
                logoDevSuffix = it.logoDevSuffix,
                websiteUrl = it.websiteUrl,
                subscriptionPageUrl = it.subscriptionPageUrl,
                canAnalyzeSubscription = it.isSubscriptionEventRuleAvailable()
            )
        }
    }


    fun addEmailSourcesFromMessages(messages: List<GmailMessage>): List<ServiceProvider> {

//        logger.debug { "[addEmailSourcesFromMessages] messages.size: ${messages.size}" }

        data class GmailSenderDto(
            val name: String?,
            val email: String,
        ) {}

        val namedSenders: List<GmailSenderDto> =
            messages.filter { it.senderName != null }.map { GmailSenderDto(it.senderName, it.senderEmail) }.distinct()

        val addressesInMessages = namedSenders.map { it.email }.distinct()
        val existingAddresses = emailSourceRepository.findExistingAddresses(addressesInMessages)
        val newSenders = namedSenders.filter { !existingAddresses.contains(it.email) }

//        logger.debug { "🔊 | [addEmailSourcesFromMessages] addressesInMessages: $addressesInMessages" }
//        logger.debug { "🔊 | [addEmailSourcesFromMessages] existingAddresses: $existingAddresses" }
//        logger.debug { "🔊 | [addEmailSourcesFromMessages] newSenders: $newSenders" }


        val aliasNameToNewEmails = newSenders.groupBy(
            { it.name!! },
            { it.email }
        )
        val updatedProviders = mutableListOf<ServiceProvider>()

        aliasNameToNewEmails.forEach { (aliasName, emails) ->
            //@TODO Optimize
            val emailOwningServiceProviders = serviceProviderRepository.findByAliasNameWithEmailSources(aliasName)
            emailOwningServiceProviders?.let { serviceProvider ->
                serviceProvider.addAllEmailSources(emails.map {
                    EmailSource(
                        null,
                        it,
                        serviceProvider = serviceProvider
                    )
                })
                updatedProviders.add(serviceProvider)
            }
        }
        if (updatedProviders.isEmpty()) {
            return emptyList()
        }
        return saveAll(updatedProviders)
    }

}
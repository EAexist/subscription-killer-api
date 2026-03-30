package com.matchalab.subscription_killer_api.subscription.service

import com.matchalab.subscription_killer_api.domain.GoogleAccount
import com.matchalab.subscription_killer_api.gmail.MessageFetchPlan
import com.matchalab.subscription_killer_api.repository.SubscriptionRepository
import com.matchalab.subscription_killer_api.service.GoogleAccountService
import com.matchalab.subscription_killer_api.subscription.EmailSource
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.subscription.ServiceProvider
import com.matchalab.subscription_killer_api.subscription.Subscription
import com.matchalab.subscription_killer_api.subscription.SubscriptionEvent
import com.matchalab.subscription_killer_api.subscription.SubscriptionEventType
import com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter.GmailClientAdapter
import com.matchalab.subscription_killer_api.subscription.service.gmailclientfactory.ProxyGmailClientFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.EntityNotFoundException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

private val logger = KotlinLogging.logger {}

@Service
class SubscriptionService(
    private val subscriptionRepository: SubscriptionRepository,
    private val serviceProviderService: ServiceProviderService,
    private val googleAccountService: GoogleAccountService,
    private val clientFactory: ProxyGmailClientFactory,
) {
    fun findByGoogleAccountAndServiceProviderIdOrCreate(
        googleAccount: GoogleAccount,
        serviceProviderId: UUID
    ): Subscription {

        return subscriptionRepository.findByGoogleAccountSubjectAndServiceProviderId(
            googleAccount.subject!!,
            serviceProviderId
        ) ?:try {
                val serviceProviderProxy = serviceProviderService.getReferenceById(serviceProviderId)

                val newSubscription = Subscription(
                    serviceProvider = serviceProviderProxy,
                    googleAccount = googleAccount
                )
                subscriptionRepository.saveAndFlush(newSubscription)

            } catch (e: DataIntegrityViolationException) {
                subscriptionRepository.findByGoogleAccountSubjectAndServiceProviderId(
                    googleAccount.subject!!,
                    serviceProviderId
                ) ?: throw e // Re-throw if it was a different constraint
            }
    }

    fun save(subscription: Subscription): Subscription {
        return subscriptionRepository.save(subscription)
    }
    fun findAllByGoogleAccountSubject(subject: String): List<Subscription> {
        return subscriptionRepository.findAllByGoogleAccountSubject(subject)
    }
    fun findAllByGoogleAccountIn(gooleAccounts: List<GoogleAccount>): List<Subscription> {
        return subscriptionRepository.findAllByGoogleAccountIn(gooleAccounts)
    }

    @Transactional
    suspend fun updateRegisteredSince(
        googleAccountSubject: String,
        serviceProviderIds: List<UUID>
    ) {

        val gmailClientAdapter: GmailClientAdapter =
            clientFactory.createAdapter(googleAccountSubject)

        val subscriptions: List<Subscription> = findAllByGoogleAccountSubject(googleAccountSubject).filter { it.id in serviceProviderIds}

        val subscriptionToFirstMessageId: Map<Subscription, String> = subscriptions.mapNotNull { subs ->
            val firstMessageId = gmailClientAdapter.getFirstMessageId(subs.serviceProvider.emailSearchAddresses)
            firstMessageId?.let {
                subs to it
            }
        }.toMap()

        val messages: List<GmailMessage> = gmailClientAdapter.getMessages(
            subscriptionToFirstMessageId.values.toList(),
            MessageFetchPlan.INTERNAL_DATE_SNIPPET_FROM_SUBJECT
        )

        val messageIdToInternalDate = messages.associate { it.id to it.internalDate }

        subscriptionToFirstMessageId.forEach { (subscription, messageId) ->
            subscription.registeredSince = messageIdToInternalDate[messageId]
        }
    }

    @Transactional
    fun update(
        googleAccountToProviders: Map<GoogleAccount, List<ServiceProvider>>
    ): List<Subscription> {
        val subjects = googleAccountToProviders.keys

        val allExistingSubs = subscriptionRepository.findAllByGoogleAccountIn(subjects)
            .groupBy { it.googleAccount.subject }

        val newEntities = mutableListOf<Subscription>()

        googleAccountToProviders.forEach { (googleAccount, providers) ->

            val existingProviderIds = allExistingSubs[googleAccount.subject]
                ?.map { it.serviceProvider.id }?.toSet() ?: emptySet()

            providers.forEach { provider ->
                if (!existingProviderIds.contains(provider.id)) {

                    val sub = Subscription(
                        serviceProvider = provider,
                        googleAccount = googleAccount
                    ).apply {
                        associateWithParents(provider, googleAccount)
                    }
                    newEntities.add(sub)
                }
            }
        }

        return if (newEntities.isNotEmpty()) subscriptionRepository.saveAll(newEntities) else emptyList()
    }

    @Transactional
    suspend fun addEvent(
        googleAccountSubject: String,
        serviceProviderId: UUID,
        eventType: SubscriptionEventType,
        internalDate: Instant
    ): SubscriptionEvent? {

        val googleAccount = googleAccountService.findById(googleAccountSubject)

        val subscription = findByGoogleAccountAndServiceProviderIdOrCreate(
            googleAccount = googleAccount,
            serviceProviderId = serviceProviderId
        )

        val subscriptionEvent = SubscriptionEvent(
            internalDate = internalDate,
            type = eventType
        )

        subscription.addEvent(subscriptionEvent)

        return subscriptionEvent
    }

}

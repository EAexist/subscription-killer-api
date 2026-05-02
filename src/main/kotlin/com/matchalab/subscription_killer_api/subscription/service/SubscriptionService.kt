package com.matchalab.subscription_killer_api.subscription.service

import com.matchalab.subscription_killer_api.domain.GoogleAccount
import com.matchalab.subscription_killer_api.gmail.MessageFetchPlan
import com.matchalab.subscription_killer_api.repository.SubscriptionRepository
import com.matchalab.subscription_killer_api.service.GoogleAccountService
import com.matchalab.subscription_killer_api.subscription.*
import com.matchalab.subscription_killer_api.subscription.dto.SubscriptionResponseDto
import com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter.GmailClientAdapter
import com.matchalab.subscription_killer_api.subscription.service.gmailclientfactory.ProxyGmailClientFactory
import com.matchalab.subscription_killer_api.utils.toDto
import io.github.oshai.kotlinlogging.KotlinLogging
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
        ) ?: try {
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

    fun saveAll(subscriptions: List<Subscription>): List<Subscription> {
        return subscriptionRepository.saveAll(subscriptions)
    }

    fun findAllByGoogleAccountSubject(subject: String): List<Subscription> {
        return subscriptionRepository.findAllByGoogleAccountSubject(subject)
    }

    fun findAllWithDetailsByGoogleAccountSubject(subject: String): List<Subscription> {
        return subscriptionRepository.findAllWithDetailsByGoogleAccountSubject(subject)
    }

    fun findAllWithDetailsByIds(ids: List<UUID>): List<Subscription> {
        return subscriptionRepository.findAllWithDetailsByIds(ids)
    }

    fun findAllByGoogleAccountIn(gooleAccounts: List<GoogleAccount>): List<Subscription> {
        return subscriptionRepository.findAllByGoogleAccountIn(gooleAccounts)
    }

    suspend fun fetchAllRegisteredSince(
        googleAccountSubject: String,
    ): List<Pair<UUID, Instant?>> {

        val gmailClientAdapter: GmailClientAdapter =
            clientFactory.createAdapter(googleAccountSubject)

        val subscriptions: List<Subscription> =
            findAllWithDetailsByGoogleAccountSubject(googleAccountSubject).filter { it.registeredSince == null }

        val subscriptionToFirstMessageId: Map<Subscription, String> =
            subscriptions.mapNotNull { subs ->
                val subjectPart =
                    subs.serviceProvider.emailSources.mapNotNull { it.subjectDiscriminator }
                        .joinToString(separator = " OR ") { "subject:\"${it}\"" }

                val fromPart =
                    subs.serviceProvider.emailSearchAddresses.joinToString(separator = " OR ") {
                        "from:\"$it\""
                    }
                val q = "$fromPart $subjectPart"

                logger.debug { "q: $q" }
                val firstMessageId =
                    gmailClientAdapter.getFirstMessageId(
                        subs.serviceProvider.emailSearchAddresses,
                        q
                    )
                firstMessageId?.let {
                    subs to it
                }
            }.toMap()

        val messages: List<GmailMessage> = gmailClientAdapter.getMessages(
            subscriptionToFirstMessageId.values.toList(),
            MessageFetchPlan.INTERNAL_DATE_SNIPPET_FROM_SUBJECT
        )

        logger.debug { "Fetched ${messages.size} messages for ${subscriptions.size} subscriptions" }

        val messageIdToInternalDate = messages.associate { it.id to it.internalDate }

        return subscriptionToFirstMessageId.map { (subscription, messageId) ->
            subscription.id!! to messageIdToInternalDate[messageId]
        }

        subscriptionToFirstMessageId.forEach { (subscription, messageId) ->
            subscription.registeredSince = messageIdToInternalDate[messageId]
        }

        subscriptionRepository.saveAll(subscriptions)
    }

    @Transactional
    fun update(
        googleAccountToProviders: Map<GoogleAccount, List<UUID>>
    ): List<Subscription> {
        val subjects = googleAccountToProviders.keys

        val allExistingSubs = subscriptionRepository.findAllByGoogleAccountIn(subjects)
            .groupBy { it.googleAccount.subject }

        val newEntities = mutableListOf<Subscription>()

        googleAccountToProviders.forEach { (googleAccount, serviceProviderIds) ->

            val existingProviderIds = allExistingSubs[googleAccount.subject]
                ?.map { it.serviceProvider.id }?.toSet() ?: emptySet()

            serviceProviderIds.forEach { serviceProviderId ->
                if (!existingProviderIds.contains(serviceProviderId)) {

                    val serviceProviderProxy =
                        serviceProviderService.getReferenceById(serviceProviderId)

                    val sub = Subscription(
                        serviceProvider = serviceProviderProxy,
                        googleAccount = googleAccount
                    ).apply {
                        associateWithParents(serviceProviderProxy, googleAccount)
                    }
                    newEntities.add(sub)
                }
            }
        }

        logger.debug { "newEntities: $newEntities" }

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

        save(subscription)

        return subscriptionEvent
    }

    fun getResponseDtos(subscriptionIds: List<UUID>): List<SubscriptionResponseDto> {
        val subscriptions = findAllWithDetailsByIds(subscriptionIds)

        return subscriptions.map { subscription ->
            val subscribedSinceDto: SubscribedSinceDto = subscription.subscribedSince()

            SubscriptionResponseDto(
                id = subscription.id!!,
                serviceProvider = subscription.serviceProvider.toDto(),
                registeredSince = subscription.registeredSince,
                hasSubscribedNewsletterOrAd = false,
                subscribedSince = subscribedSinceDto.subscribedSince,
                isNotSureIfSubscriptionIsOngoing = subscribedSinceDto.isNotSureIfSubscriptionIsOngoing,
                nextPaymentDate = subscribedSinceDto.nextPaymentDate
            )

        }
    }

}

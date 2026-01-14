package com.matchalab.subscription_killer_api.subscription.service

import com.matchalab.subscription_killer_api.domain.GoogleAccount
import com.matchalab.subscription_killer_api.repository.SubscriptionRepository
import com.matchalab.subscription_killer_api.subscription.Subscription
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.util.*

private val logger = KotlinLogging.logger {}

@Service
class SubscriptionService(
    private val subscriptionRepository: SubscriptionRepository,
    private val serviceProviderService: ServiceProviderService,
) {
    fun findByGoogleAccountAndServiceProviderIdOrCreate(
        googleAccount: GoogleAccount,
        serviceProviderId: UUID
    ): Subscription {
        return subscriptionRepository.findByGoogleAccountSubjectAndServiceProviderId(
            googleAccount.subject!!,
            serviceProviderId
        )
            ?: run {
                val serviceProvider = serviceProviderService.findByIdOrNotFound(serviceProviderId)
                val subscription = Subscription(serviceProvider = serviceProvider, googleAccount = googleAccount)
                subscription.associateWithParents(serviceProvider, googleAccount)
                subscriptionRepository.save(subscription)
            }
    }

    fun save(subscription: Subscription): Subscription {
        return subscriptionRepository.save(subscription)
    }
}
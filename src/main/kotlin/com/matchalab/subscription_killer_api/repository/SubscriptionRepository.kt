package com.matchalab.subscription_killer_api.repository

import com.matchalab.subscription_killer_api.subscription.Subscription
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.*

interface SubscriptionRepository : JpaRepository<Subscription, UUID> {
    @Query("SELECT COUNT(s) FROM Subscription s WHERE s.serviceProvider.id = :serviceProviderId")
    fun countByServiceProviderId(serviceProviderId: UUID): Long
}

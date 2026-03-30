package com.matchalab.subscription_killer_api.subscription

import com.matchalab.subscription_killer_api.domain.GoogleAccount
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.*

@Entity
@Table(
    uniqueConstraints =
        [
            UniqueConstraint(
                name = "uk_report_provider",
                columnNames = ["google_account_id", "service_provider_id"]
            )]
)
class Subscription(

    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    var id: UUID? = null,

    /*@TODO: When the db gets large (>100), consider migrating to Relations*/
    @JdbcTypeCode(SqlTypes.JSON)
    var subscriptionEvents: MutableList<SubscriptionEvent> = mutableListOf(),

    var registeredSince: Instant? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_provider_id", nullable = false)
    var serviceProvider: ServiceProvider,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "google_account_id", nullable = false)
    var googleAccount: GoogleAccount,
) {
    fun associateWithParents(serviceProvider: ServiceProvider, googleAccount: GoogleAccount) {
        this.serviceProvider = serviceProvider
        this.googleAccount = googleAccount
    }

    fun addEvent(subscriptionEvent : SubscriptionEvent) {
        this.subscriptionEvents.add(subscriptionEvent)
    }
}

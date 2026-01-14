package com.matchalab.subscription_killer_api.subscription

import com.matchalab.subscription_killer_api.domain.GoogleAccount
import jakarta.persistence.*
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

    var registeredSince: Instant? = null,
    var hasSubscribedNewsletterOrAd: Boolean = false,
    var subscribedSince: Instant? = null,
    var isNotSureIfSubscriptionIsOngoing: Boolean = false,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_provider_id", nullable = false)
    var serviceProvider: ServiceProvider,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "google_account_id", nullable = false)
    var googleAccount: GoogleAccount? = null,
) {
    fun associateWithParents(serviceProvider: ServiceProvider, googleAccount: GoogleAccount) {
        this.serviceProvider = serviceProvider
        serviceProvider.subscriptions.add(this)

        this.googleAccount = googleAccount
        googleAccount.subscriptions.add(this)
    }
}

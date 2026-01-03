package com.matchalab.subscription_killer_api.domain

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.matchalab.subscription_killer_api.subscription.Subscription
import jakarta.persistence.*
import java.time.Instant

@Entity
class GoogleAccount(
    @Id var subject: String? = null,
    var name: String,
    var email: String,

    // Google OAuth
    @Column(columnDefinition = "TEXT")
//        @Convert(converter = TokenEncryptionConverter::class)
    var refreshToken: String? = null,

    @Column(columnDefinition = "TEXT")
    var accessToken: String? = null,

    var expiresAt: Instant? = null,
    var scope: String? = null,

    // Subscriptions
    var analyzedAt: Instant? = null,
    @OneToMany(mappedBy = "googleAccount", cascade = [CascadeType.ALL], orphanRemoval = true)
    var subscriptions: MutableList<Subscription> = mutableListOf(),

    // Bidirectional Owning Entities
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_user_id", nullable = false)
    var appUser: AppUser? = null
) {
    constructor(
        payload: GoogleIdToken.Payload
    ) : this(
        subject = payload.subject,
        email = payload.email,
        name = payload.get("name") as? String ?: "Unknown",
    )

    fun updateRefreshToken(refreshToken: String) {
        this.refreshToken = refreshToken
//        this.expiresAt = expiresAt
    }

    fun updateAccessToken(refreshToken: String, expiresAt: Instant?) {
        this.refreshToken = refreshToken
        this.expiresAt = expiresAt
    }

    fun addSubscription(subscription: Subscription) {
        this.subscriptions.add(subscription)
        subscription.googleAccount = this
    }

    fun updateSubscriptions(subscriptions: List<Subscription>) {
        this.subscriptions.clear()
        subscriptions.forEach { addSubscription(it) }
    }
}

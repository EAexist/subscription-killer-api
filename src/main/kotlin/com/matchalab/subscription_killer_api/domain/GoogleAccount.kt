package com.matchalab.subscription_killer_api.domain

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne

@Entity
class GoogleAccount(
        @Id var subject: String? = null,
        var name: String,
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "appUser_id", nullable = false)
        var appUser: AppUser? = null
) {
    constructor(
            payload: GoogleIdToken.Payload
    ) : this(
            subject = payload.subject,
            name = payload.get("name") as? String ?: "Unknown",
    )
}

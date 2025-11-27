package com.matchalab.subscription_killer_api.domain

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
) {}

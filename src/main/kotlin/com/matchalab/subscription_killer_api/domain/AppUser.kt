package com.matchalab.subscription_killer_api.domain

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import java.util.UUID

@Entity
class AppUser(
        @Id @GeneratedValue(strategy = GenerationType.AUTO) var id: UUID? = null,
        var name: String,
        @OneToMany(mappedBy = "appUser", cascade = [CascadeType.ALL], orphanRemoval = true)
        val googleAccounts: MutableList<GoogleAccount> = mutableListOf()
) {
    fun addGoogleAccount(googleAccount: GoogleAccount) {
        this.googleAccounts.add(googleAccount)
        googleAccount.appUser = this
    }
}

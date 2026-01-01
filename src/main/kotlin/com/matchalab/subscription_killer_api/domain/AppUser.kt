package com.matchalab.subscription_killer_api.domain

import jakarta.persistence.*
import java.util.*

@Entity
class AppUser(

    @Id @GeneratedValue(strategy = GenerationType.AUTO) var id: UUID? = null,
    var name: String,

    @Enumerated(EnumType.STRING)
    var userRole: UserRoleType = UserRoleType.USER,
    
    @OneToMany(mappedBy = "appUser", cascade = [CascadeType.ALL], orphanRemoval = true)
    var googleAccounts: MutableList<GoogleAccount> = mutableListOf()
) {
    fun addGoogleAccount(googleAccount: GoogleAccount) {
        this.googleAccounts.add(googleAccount)
        googleAccount.appUser = this
    }
}

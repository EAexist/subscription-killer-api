package com.matchalab.subscription_killer_api.config

import com.matchalab.subscription_killer_api.security.CustomUserDetails
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority

class GoogleIdTokenAuthenticationToken(
        private val idToken: String,
        authorities: Collection<GrantedAuthority>
) : AbstractAuthenticationToken(authorities) {
    init {
        isAuthenticated = false
    }

    private var principal: CustomUserDetails? = null

    constructor(
            principal: CustomUserDetails,
            authorities: Collection<GrantedAuthority>
    ) : this("", authorities) {
        this.principal = principal
        isAuthenticated = true
    }

    override fun getCredentials(): Any = idToken

    override fun getPrincipal(): CustomUserDetails? {
        return principal
    }

    // override fun isAuthenticated(): Boolean {
    //     return super.isAuthenticated()
    // }
}

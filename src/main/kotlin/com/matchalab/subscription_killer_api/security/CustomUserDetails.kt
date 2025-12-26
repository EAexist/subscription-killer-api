package com.matchalab.subscription_killer_api.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.OidcUserInfo
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import java.io.Serializable
import java.util.*


class CustomOidcUser(
    val appUserId: UUID?,
    val authoritiesInternal: Collection<GrantedAuthority>,
    val oidcUser: OidcUser? = null
) : OidcUser, Serializable {

    override fun getName(): String? {
        return appUserId?.toString()
    }

    // Delegate other OidcUser methods to oidcUser...
    override fun getAttributes(): MutableMap<String?, Any?>? {
        return oidcUser!!.attributes
    }

    override fun getAuthorities(): Collection<GrantedAuthority> {
        return authoritiesInternal
    }

    override fun getClaims(): MutableMap<String?, Any?>? {
        return oidcUser!!.claims
    }

    override fun getIdToken(): OidcIdToken? {
        return oidcUser!!.idToken
    }

    override fun getUserInfo(): OidcUserInfo? {
        return oidcUser!!.userInfo
    }
}

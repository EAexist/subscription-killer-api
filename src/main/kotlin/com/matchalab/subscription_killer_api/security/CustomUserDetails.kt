package com.matchalab.subscription_killer_api.security

import com.matchalab.subscription_killer_api.domain.AppUser
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

class CustomUserDetails(val appUser: AppUser, val isNew: Boolean = false) : UserDetails {

    companion object {
        private const val NO_PASSWORD = "{noop}"
    }

    override fun getAuthorities(): Collection<GrantedAuthority> {
        return listOf(SimpleGrantedAuthority(appUser.userRole.authority))
    }

    override fun getPassword(): String {
        return NO_PASSWORD
    }

    override fun getUsername(): String {
        return appUser.id.toString()
    }

    override fun isAccountNonExpired(): Boolean {
        return true
    }
}

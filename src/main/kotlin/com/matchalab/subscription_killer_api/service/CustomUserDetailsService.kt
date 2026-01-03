package com.matchalab.subscription_killer_api.service

import com.matchalab.subscription_killer_api.domain.AppUser
import com.matchalab.subscription_killer_api.security.CustomOidcUser
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Service


private val logger = KotlinLogging.logger {}

@Service
class CustomOidcUserService(private val appUserService: AppUserService) : OidcUserService() {

    override fun loadUser(userRequest: OidcUserRequest?): OidcUser {
        val oidcUser = super.loadUser(userRequest)
        val googleSub = oidcUser.subject

        val appUser: AppUser = appUserService.findByGoogleAccounts_Subject(googleSub)
            ?: appUserService.register(oidcUser)

        val authorities: Collection<SimpleGrantedAuthority> =
            listOf(SimpleGrantedAuthority(appUser.userRole.authority))

        return CustomOidcUser(appUser.id, authorities, oidcUser)
    }
}

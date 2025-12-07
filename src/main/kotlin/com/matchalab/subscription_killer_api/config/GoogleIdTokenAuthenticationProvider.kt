package com.matchalab.subscription_killer_api.config

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.matchalab.subscription_killer_api.domain.LoginOrRegisterResult
import com.matchalab.subscription_killer_api.security.CustomUserDetails
import com.matchalab.subscription_killer_api.service.AppUserService
import com.matchalab.subscription_killer_api.service.TokenVerifierService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority

private val logger = KotlinLogging.logger {}

class GoogleIdTokenAuthenticationProvider(
        // private val userDetailsService: UserDetailsService,
        private val tokenVerifierService: TokenVerifierService,
        private val appUserService: AppUserService,
) : AuthenticationProvider {

    override fun authenticate(authentication: Authentication): Authentication {

        logger.debug { "GoogleIdTokenAuthenticationProvider.authenticate()" }

        val idToken = authentication.credentials as String
        val verifiedIdTokenPayload: GoogleIdToken.Payload? =
                tokenVerifierService.verifyToken(idToken)

        if (verifiedIdTokenPayload == null) {
            throw BadCredentialsException("Invalid Google ID Token.")
        }

        val loginOrRegisterResult: LoginOrRegisterResult =
                appUserService.findByGoogleAccountSubjectOrRegister(verifiedIdTokenPayload)

        val principal: CustomUserDetails =
                CustomUserDetails(
                        appUser = loginOrRegisterResult.appUser,
                        isNew = loginOrRegisterResult is LoginOrRegisterResult.Registered
                )
        return GoogleIdTokenAuthenticationToken(
                principal = principal,
                authorities =
                        listOf(
                                SimpleGrantedAuthority(
                                        loginOrRegisterResult.appUser.userRole.authority
                                )
                        )
        )
    }

    override fun supports(authentication: Class<*>?): Boolean {
        return authentication == GoogleIdTokenAuthenticationToken::class.java
    }
}

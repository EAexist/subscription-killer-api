package com.matchalab.subscription_killer_api.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class AuthService() {

    // fun loginOrRegister(idToken: String): LoginOrRegisterResult {
    //     // logger.debug { "Controller received idToken=${request.idToken}" }

    //     // val idToken = loginRequest.idToken
    //     // if (idToken.isNullOrBlank()) {
    //     //     throw BadCredentialsException("❌ ID Token parameter is missing")
    //     // }

    //     // val authResult =
    //     //         authenticationManager.authenticate(
    //     //                 GoogleIdTokenAuthenticationToken(
    //     //                         idToken,
    //     //                         listOf(SimpleGrantedAuthority(UserRoleType.USER.authority))
    //     //                 )
    //     //         )

    //     // SecurityContextHolder.getContext().authentication = authResult

    //     // val responseBody = AppUserResponseDto("fakeName", listOf())

    //     return LoginOrRegisterResult.Registered(AppUserResponseDto(idToken, listOf()))
    // }
}

package com.matchalab.subscription_killer_api.controller

import com.matchalab.subscription_killer_api.config.GuestAppUserProperties
import com.matchalab.subscription_killer_api.security.CustomOidcUser
import com.matchalab.subscription_killer_api.service.AppUserService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/api/v1/guest")
class GuestAuthController(
    private val appUserService: AppUserService,
    private val guestAppUserProperties: GuestAppUserProperties,
) {
    @GetMapping
    fun loginAsGuest(request: HttpServletRequest): ResponseEntity<Void> {

        val guestAppUser = appUserService.findByGoogleAccounts_Subject(guestAppUserProperties.subject)!!
        val authorities = listOf(SimpleGrantedAuthority(guestAppUser.userRole.authority))
//        val mockAttributes = mapOf(
//            "sub" to "guest_sub_1234",
//            "name" to "Guest User",
//            "email" to guestAppUser.email
//        )

        val guestOidcUser = CustomOidcUser(
            guestAppUser.id,
            authorities,
            DefaultOidcUser(authorities, OidcIdToken.withTokenValue("mock").claims { claims ->
                claims["sub"] = guestAppUser.googleAccounts[0].subject
                claims["email"] = guestAppUser.googleAccounts[0].email
                claims["name"] = guestAppUser.googleAccounts[0].name
                claims["preferred_username"] = guestAppUser.name
            }.build())
        )

        val authentication = OAuth2AuthenticationToken(
            guestOidcUser, authorities, "google"
        )

        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)


        val session = request.getSession(true)
        session.setAttribute("SPRING_SECURITY_CONTEXT", context)
        return ResponseEntity.status(HttpStatus.OK).build<Void>()
    }
}

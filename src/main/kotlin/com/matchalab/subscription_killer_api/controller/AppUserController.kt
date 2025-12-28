package com.matchalab.subscription_killer_api.controller

import com.matchalab.subscription_killer_api.core.dto.AppUserResponseDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/appUser")
class AuthController() {

    @GetMapping
    fun getAppUser(
        @AuthenticationPrincipal customUserDetails: OAuth2User,
//        @AuthenticationPrincipal customUserDetails: CustomUserDetails?

    ): ResponseEntity<AppUserResponseDto> {
        if (customUserDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        return ResponseEntity.status(HttpStatus.OK)
//            .body(customUserDetails.appUser.toResponseDto())
            .body(AppUserResponseDto("name", listOf()))
    }
}

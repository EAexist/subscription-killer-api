package com.matchalab.subscription_killer_api.controller

import com.matchalab.subscription_killer_api.core.dto.AppUserResponseDto
import com.matchalab.subscription_killer_api.security.CustomUserDetails
import com.matchalab.subscription_killer_api.service.AuthService
import com.matchalab.subscription_killer_api.utils.toResponseDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping
    fun loginOrRegister(
            // @RequestBody loginRequestDto: LoginRequestDto,
            @AuthenticationPrincipal customUserDetails: CustomUserDetails?
    ): ResponseEntity<AppUserResponseDto> {
        if (customUserDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        return when (customUserDetails.isNew) {
            true ->
                    ResponseEntity.status(HttpStatus.CREATED)
                            .body(customUserDetails.appUser.toResponseDto())
            false ->
                    ResponseEntity.status(HttpStatus.OK)
                            .body(customUserDetails.appUser.toResponseDto())
        }
    }
}

package com.matchalab.subscription_killer_api.controller

import com.matchalab.subscription_killer_api.config.AuthenticatedUser
import com.matchalab.subscription_killer_api.core.dto.AppUserResponseDto
import com.matchalab.subscription_killer_api.service.AppUserService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/appUsers")
class AppUserController(private val appUserService: AppUserService) {

    @GetMapping
    fun getAppUser(
        @AuthenticatedUser appUserId: UUID
    ): ResponseEntity<AppUserResponseDto> {
        val appUserResponseDto = appUserService.getAppUser(appUserId)
        return ResponseEntity.ok(appUserResponseDto)
    }
}

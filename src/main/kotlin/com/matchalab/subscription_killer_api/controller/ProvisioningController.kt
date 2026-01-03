package com.matchalab.subscription_killer_api.controller

import com.matchalab.subscription_killer_api.config.AuthenticatedUser
import com.matchalab.subscription_killer_api.service.ProvisioningService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/provisioning")
class ProvisioningController(private val provisioningService: ProvisioningService) {

    @PostMapping
    fun triggerProvisioning(
        @AuthenticatedUser appUserId: UUID
    ): ResponseEntity<Void> {
        provisioningService.provisionResources(appUserId)
        return ResponseEntity.accepted().build()
    }
}

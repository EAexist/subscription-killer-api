package com.matchalab.subscription_killer_api.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class PingController {

    @GetMapping("/ping")
    fun ping(): String {
        return "Welcome to Subscription Killer API Endpoint"
    }
}

data class PingResponse(val message: String = "Welcome to Subscription Killer API Endpoint")

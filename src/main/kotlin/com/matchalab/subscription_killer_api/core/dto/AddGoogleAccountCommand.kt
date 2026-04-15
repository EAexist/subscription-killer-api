package com.matchalab.subscription_killer_api.core.dto

import java.time.Instant

data class AddGoogleAccountCommand(
    val subject: String,
    val name: String,
    val email: String,
    val refreshToken: String? = null,
    val accessToken: String? = null,
    val expiresAt: Instant? = null,
    val scope: String? = null,
)
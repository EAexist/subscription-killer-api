package com.matchalab.subscription_killer_api.exception

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(HttpStatus.UNAUTHORIZED)
class GoogleOAuthReAuthRequiredException(
    message: String = "Google OAuth refresh token expired, re-authentication required"
) : RuntimeException(message)

package com.matchalab.subscription_killer_api.utils

import com.matchalab.subscription_killer_api.core.dto.AppUserResponseDto
import com.matchalab.subscription_killer_api.core.dto.GoogleAccountResponseDto
import com.matchalab.subscription_killer_api.domain.AppUser
import com.matchalab.subscription_killer_api.domain.GoogleAccount

fun AppUser.toResponseDto(): AppUserResponseDto {
    return AppUserResponseDto(
            name = this.name,
            googleAccounts = this.googleAccounts.map(GoogleAccount::toDto)
    )
}

fun GoogleAccount.toDto(): GoogleAccountResponseDto {
    return GoogleAccountResponseDto(
            subject = this.subject
                            ?: throw IllegalStateException(
                                    "Google Account subject cannot be null for DTO mapping."
                            ),
            name = this.name,
    )
}

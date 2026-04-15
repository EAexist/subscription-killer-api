package com.matchalab.subscription_killer_api.core.dto

data class AppUserResponseDto(
    val name: String,
    val googleAccounts: List<GoogleAccountResponseDto>,
) {}

package com.matchalab.sublog_api.core.dto

data class AppUserResponseDto(
    val name: String,
    val googleAccounts: List<GoogleAccountResponseDto>,
) {}

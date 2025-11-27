package com.matchalab.subscription_killer_api.core.dto

data class AppUserResponseDTO(
        // var id: UUID,
        val name: String,
        val googleAccounts: List<GoogleAccountResponseDTO>,
) {}

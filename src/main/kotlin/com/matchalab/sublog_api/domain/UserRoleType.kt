package com.matchalab.sublog_api.domain

enum class UserRoleType(val authority: String, val displayName: String) {
    ADMIN("ROLE_ADMIN", "Administrator"),
    USER("ROLE_USER", "Standard User"),
    // GUEST("ROLE_GUEST", "Guest Access")
}

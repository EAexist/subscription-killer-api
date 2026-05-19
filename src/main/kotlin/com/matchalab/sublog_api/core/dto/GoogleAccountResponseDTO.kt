package com.matchalab.sublog_api.core.dto

data class GoogleAccountResponseDto(
    val subject: String,
    val name: String,
    val email: String,
    val canDelete: Boolean
) {}

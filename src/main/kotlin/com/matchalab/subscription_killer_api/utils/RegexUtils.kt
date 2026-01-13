package com.matchalab.subscription_killer_api.utils

fun String.containsRegex(pattern: String, ignoreCase: Boolean = true): Boolean {
    if (pattern.isBlank()) return false

    val options = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
    return pattern.toRegex(options).containsMatchIn(this)
}

fun String.containsPattern(pattern: String): Boolean {
    return pattern.toRegex(RegexOption.IGNORE_CASE).containsMatchIn(this)
}
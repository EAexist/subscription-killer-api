package com.matchalab.sublog_api.emailtemplate

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

fun String.extractAnchors(): List<String> {

    val placeholderRegex = Regex("\\{\\{.*?\\}\\}") // Matches the {{placeholder}} pattern

    val anchors = this.split(placeholderRegex)
        .map { it.trim() }
        .filter { anchor ->
            // Only keep if it contains at least one letter
            anchor.any { it.isLetter() }
        }

    // Validate all anchors are trimmed (following EmailTemplate validation pattern)
    require(anchors.all { it == it.trim() }) {
        "extractAnchors produced untrimmed strings"
    }

    return anchors
}

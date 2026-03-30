package com.matchalab.subscription_killer_api.emailtemplate

data class EmailTemplate(
    val subjectAnchors: List<String>,
    val snippetAnchors: List<String>,
) {
    init {
        // Validate Subject Anchors
        require(subjectAnchors.all { it == it.trim() && it.any { char -> char.isLetter() } }) {
            "subjectAnchors must be trimmed and contain at least one letter."
        }

        // Validate Snippet Anchors
        require(snippetAnchors.all { it == it.trim() && it.any { char -> char.isLetter() } }) {
            "snippetAnchors must be trimmed and contain at least one letter."
        }
    }
}
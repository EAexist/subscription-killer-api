package com.matchalab.subscription_killer_api.emailtemplate.service

import com.matchalab.subscription_killer_api.emailtemplate.EmailTemplate
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

@Service
class EmailTemplateMatchService(
) {
    private val regexCache = ConcurrentHashMap<String, Regex>()

    fun matchMessage(
        template: EmailTemplate,
        message: GmailMessage,
    ): Boolean {

        val subjectMatch: Boolean = matches(message.subject, template.subjectAnchors)
        val snippetMatch: Boolean = matches(message.snippet, template.snippetAnchors)
        return subjectMatch && snippetMatch
    }

    fun matches(text: String, anchors: List<String>): Boolean {
        // @Validate anchors must be trimmed @EmailTemplate
        require(anchors.all { it == it.trim() }) {
            "anchors contains untrimmed strings"
        }

        val normalizedText = text.replace(Regex("\\s+"), " ").trim()

        var currentPos = 0
        for (anchor in anchors) {
            // Case-insensitive search
            val index = normalizedText.indexOf(anchor, currentPos, ignoreCase = true)

            if (index == -1) return false

            currentPos = index + anchor.length
        }

        return true
    }

    fun evictCache() {
        regexCache.clear()
    }
}

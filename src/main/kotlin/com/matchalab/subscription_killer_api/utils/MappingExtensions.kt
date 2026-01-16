package com.matchalab.subscription_killer_api.utils

import com.google.api.services.gmail.model.Message
import com.matchalab.subscription_killer_api.core.dto.AppUserResponseDto
import com.matchalab.subscription_killer_api.core.dto.GoogleAccountResponseDto
import com.matchalab.subscription_killer_api.domain.AppUser
import com.matchalab.subscription_killer_api.domain.GoogleAccount
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.subscription.ServiceProvider
import com.matchalab.subscription_killer_api.subscription.Subscription
import com.matchalab.subscription_killer_api.subscription.dto.AccountReportDto
import com.matchalab.subscription_killer_api.subscription.dto.ServiceProviderResponseDto
import com.matchalab.subscription_killer_api.subscription.dto.SubscriptionResponseDto
import com.matchalab.subscription_killer_api.subscription.service.GmailMessageSummaryDto
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

fun AppUser.toResponseDto(): AppUserResponseDto {
    return AppUserResponseDto(
        name = this.name,
        googleAccounts = this.googleAccounts.map(GoogleAccount::toResponseDto)
    )
}

fun GoogleAccount.toResponseDto(): GoogleAccountResponseDto {
    return GoogleAccountResponseDto(
        email = this.email,
        name = this.name,
    )
}

fun GoogleAccount.toReportDto(): AccountReportDto {
    return AccountReportDto(
        subscriptions = this.subscriptions.map { it -> it.toResponseDto() },
        googleAccount = this.toResponseDto(),
    )
}

fun Subscription.toResponseDto(): SubscriptionResponseDto {
    return SubscriptionResponseDto(
        serviceProvider = this.serviceProvider.toDto(),
        registeredSince = this.registeredSince,
        hasSubscribedNewsletterOrAd = this.hasSubscribedNewsletterOrAd,
        subscribedSince = this.subscribedSince,
        isNotSureIfSubscriptionIsOngoing = this.isNotSureIfSubscriptionIsOngoing,
    )
}

fun ServiceProvider.toDto(): ServiceProviderResponseDto {
    return ServiceProviderResponseDto(
        this.id!!,
        this.displayName,
        this.logoDevSuffix,
        this.websiteUrl,
        this.subscriptionPageUrl,
        this.isEmailDetectionRuleAvailable()
    )
}

fun Message.toGmailMessage(maxSnippetSize: Int = 400): GmailMessage {

    val doHidePrices = true

    val internalDate = this.internalDate.let { DateTimeUtils.epochMilliToInstant(it) }
    val headers = this.payload?.headers
    val fromHeaderValue =
        headers?.find { it.name.equals("From", ignoreCase = true) }?.value ?: ""
    val subjectHeaderValue =
        headers?.find { it.name.equals("Subject", ignoreCase = true) }?.value ?: ""

    val regex = """^(.+)\s+<(.+)>$""".toRegex()
    val matchResult = regex.find(fromHeaderValue)
    val (name, email) = (matchResult?.destructured?.toList() ?: listOf(
        "",
        fromHeaderValue.trim()
    ))

    return GmailMessage(
        id = this.id,
        senderName = name,
        senderEmail = email,
        subject = subjectHeaderValue.cleanEmailText().let { if (doHidePrices) it.hidePrices() else it },
        internalDate = internalDate,
        snippet = this.snippet.cleanEmailText().let { if (doHidePrices) it.hidePrices() else it }.take(maxSnippetSize)
    )
}

private fun String.cleanEmailText(): String {
    return this
        // 1. Decode common HTML entities found in your logs
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        // 2. Remove invisible/control/format characters (\p{C}) AND specific markers like the "͏" (Combining Grapheme Joiner)
        .replace(Regex("[\\p{C}\\u034F\\u200B-\\u200D\\uFEFF]"), "")
        // 3. Collapse all whitespace (newlines, tabs, multiple spaces) into one single space
        .replace(Regex("\\s+"), " ")
        .trim()
}

fun GmailMessage.toSummaryDto(): GmailMessageSummaryDto =
    GmailMessageSummaryDto(this.id, this.subject, this.snippet)

fun String.hidePrices(): String {
    // Matches the full number followed by the currency suffix
    val priceSuffixRegex = """\d[\d,.]*\s?(원|KRW|USD)""".toRegex(RegexOption.IGNORE_CASE)
    // Matches the currency symbol followed by the full number
    val pricePrefixRegex = """(\$|€|£|₩|USD)\s?\d[\d,.]*""".toRegex(RegexOption.IGNORE_CASE)

    return this
        .replace(priceSuffixRegex, "[PRICE]")
        .replace(pricePrefixRegex, "[PRICE]")
}

fun String.hideDates(): String {
    val dateAndOptionalTimeRegex =
        """(?i)\b(\d{4}-\d{2}-\d{2}|\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]* \d{1,2}(?:st|nd|rd|th)?,? \d{4})(\s+\d{1,2}:\d{2}(:\d{2})?)?\b""".toRegex(
            RegexOption.IGNORE_CASE
        )
    val koreanDateRegex = """\d{4}년\s?\d{1,2}월\s?\d{1,2}일""".toRegex()

    return this.replace(dateAndOptionalTimeRegex, "[DATE]")
        .replace(koreanDateRegex, "[DATE]")
}


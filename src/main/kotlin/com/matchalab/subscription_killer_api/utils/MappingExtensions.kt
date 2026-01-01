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
import com.matchalab.subscription_killer_api.subscription.dto.ServiceProviderDto
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
        analyzedAt = this.analyzedAt
    )
}

fun Subscription.toResponseDto(): SubscriptionResponseDto {
    return SubscriptionResponseDto(
        serviceProvider = this.serviceProvider.toDto(),
        registeredSince = this.registeredSince,
        hasSubscribedNewsletterOrAd = this.hasSubscribedNewsletterOrAd,
        paidSince = this.paidSince,
        isNotSureIfPaymentIsOngoing = this.isNotSureIfPaymentIsOngoing,
    )
}

fun ServiceProvider.toDto(): ServiceProviderDto {
    return ServiceProviderDto(
        this.displayName,
        this.isEmailDetectionRuleAvailable()
    )
}

fun Message.toGmailMessage(): GmailMessage? {

//    logger.debug { "Message.toGmailMessage() Message: ${this.toString()}" }
    val internalDate = this.internalDate.let { DateTimeUtils.epochMilliToInstant(it) }
    val headers = this.payload?.headers ?: return null
    val fromHeaderValue =
        headers.find { it.name.equals("From", ignoreCase = true) }?.value ?: return null
    val subjectHeaderValue =
        headers.find { it.name.equals("Subject", ignoreCase = true) }?.value ?: ""

    val regex = """^(.+)\s+<(.+)>$""".toRegex()
    val matchResult = regex.find(fromHeaderValue)
    val (name, email) = matchResult?.destructured?.toList() ?: listOf("", "")

    return GmailMessage(
        id = this.id,
        senderName = name,
        senderEmail = email,
        subject = subjectHeaderValue,
        internalDate = internalDate,
        snippet = this.snippet ?: "",
    )
}

fun GmailMessage.toSummaryDto(): GmailMessageSummaryDto =
    GmailMessageSummaryDto(this.subject, this.snippet)


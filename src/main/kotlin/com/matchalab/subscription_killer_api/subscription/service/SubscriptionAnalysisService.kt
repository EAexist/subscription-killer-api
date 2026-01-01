package com.matchalab.subscription_killer_api.subscription.service

import com.matchalab.subscription_killer_api.domain.GoogleAccount
import com.matchalab.subscription_killer_api.gmail.MessageFetchPlan
import com.matchalab.subscription_killer_api.repository.GoogleAccountRepository
import com.matchalab.subscription_killer_api.service.AppUserService
import com.matchalab.subscription_killer_api.subscription.*
import com.matchalab.subscription_killer_api.subscription.analysisStep.AnalysisProgressStatusDto
import com.matchalab.subscription_killer_api.subscription.analysisStep.AnalysisStatusType
import com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter.GmailClientAdapter
import com.matchalab.subscription_killer_api.subscription.service.gmailclientfactory.GmailClientFactory
import com.matchalab.subscription_killer_api.utils.DateTimeUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*


private val logger = KotlinLogging.logger {}


@ConfigurationProperties(prefix = "app.mail")
data class MailProperties(val analysisMonths: Long)

@Service
class SubscriptionAnalysisService(
    private val googleAccountRepository: GoogleAccountRepository,
    private val appUserService: AppUserService,
    private val serviceProviderService: ServiceProviderService,
    private val clientFactory: GmailClientFactory,
    private val mailProperties: MailProperties,
    private val progressService: ProgressService
) {

    val after: Instant = DateTimeUtils.minusMonthsFromInstant(Instant.now(), mailProperties.analysisMonths)
    val MAX_GAP_DAYS: Int = 45

    data class PaidSinceDto(
        val paidSince: Instant?,
        val isNotSureIfPaymentIsOngoing: Boolean = false,
    )

    data class SubscriptionDto(
        val serviceProviderId: UUID,
        val registeredSince: Instant?,
        val paidSince: Instant?,
        val isNotSureIfPaymentIsOngoing: Boolean,
        val hasSubscribedNewsletterOrAd: Boolean
    )

    suspend fun analyze(appUserId: UUID) {

        val googleAccountSubjects: List<String> = appUserService.findGoogleAccountSubjectsByAppUserId(appUserId)

        coroutineScope {
            googleAccountSubjects.map { subject ->
                async(Dispatchers.IO) {
                    // @TOOD Prevent Frequent Re-analysis
                    val subscriptionDtos: List<SubscriptionDto> = analyzeSingleGoogleAccount(appUserId, subject)
                    saveAndMapToDto(subject, subscriptionDtos)
                }
            }
        }.awaitAll()
    }

    fun saveAndMapToDto(subject: String, subscriptionDtos: List<SubscriptionDto>) {
        val googleAccount: GoogleAccount =
            googleAccountRepository.findByIdWithSubscriptionsAndProviders(subject)
                ?: throw IllegalStateException(
                    "Google Account not found for subject=$subject"
                )
        subscriptionDtos.forEach { it ->
            val serviceProvider =
                serviceProviderService.findByIdWithSubscriptions(it.serviceProviderId) ?: throw IllegalStateException()
            val subscription = Subscription(
                registeredSince = it.registeredSince,
                hasSubscribedNewsletterOrAd = it.hasSubscribedNewsletterOrAd,
                paidSince = it.paidSince,
                isNotSureIfPaymentIsOngoing = it.isNotSureIfPaymentIsOngoing,
                serviceProvider = serviceProvider,
                googleAccount = googleAccount
            )
            subscription.associateWithParents(serviceProvider, googleAccount)
        }
        googleAccount.analyzedAt = Instant.now()
        googleAccountRepository.save(googleAccount)
    }

    suspend fun analyzeSingleGoogleAccount(appUserId: UUID, googleAccountSubject: String): List<SubscriptionDto> {
        try {

            logger.debug { "[analyzeSingleGoogleAccount] googleAccountSubject: $googleAccountSubject" }

            // List Gmail Messages
            val gmailClientAdapter: GmailClientAdapter =
                clientFactory.createAdapter(googleAccountSubject)
            val afterPart: String = "after:${after.epochSecond}"

            val allServiceProviders: List<ServiceProvider> = serviceProviderService.findAllWithEmailSourcesAndAliases()

            val allEmailAddressesAndAliasNames: List<String> = allServiceProviders.flatMap {
                it.emailSearchAddresses + (it.emailSearchAliasNames?.values ?: emptyList())
            }

            if (allEmailAddressesAndAliasNames.isEmpty()) {
                return emptyList()
            }

            val fromPart = allEmailAddressesAndAliasNames.joinToString(separator = " OR ") {
                "from:\"$it\""
            }
            val listMessageQuery = String.format("%s (%s)", afterPart, fromPart)
            val allMessageIds: List<String> = gmailClientAdapter.listMessageIds(listMessageQuery)

            val allMessages: List<GmailMessage> =
                gmailClientAdapter.getMessages(allMessageIds, MessageFetchPlan.INTERNAL_DATE_SNIPPET_FROM_SUBJECT)

            progressService.setProgress(
                appUserId,
                googleAccountSubject,
                AnalysisProgressStatusDto(AnalysisStatusType.EMAIL_FETCHED)
            )

            // Add New Email Addresses identified from aliasNames
            // @TODO: optimize
            serviceProviderService.addEmailSourcesFromMessages(allMessages)

            // Analyze Subscription Status
            val uniqueAddresses = allMessages.map { it.senderEmail }.distinct()
            val serviceProviders = serviceProviderService.findByActiveEmailAddressesInWithEmailSources(uniqueAddresses)

            val addressToServiceProvider = serviceProviders.flatMap { serviceProvider ->
                serviceProvider.emailSources.map { it.targetAddress to serviceProvider }
            }.toMap()

            val serviceProviderToAddressToMessages = allMessages
                .groupBy { message -> addressToServiceProvider[message.senderEmail]!! }
                .mapValues { (_, messages) ->
                    messages.groupBy { it.senderEmail }
                }

            val subscriptions: List<SubscriptionDto> = coroutineScope {
                serviceProviderToAddressToMessages.mapNotNull { (serviceProvider, addressToMessages) ->
                    async(Dispatchers.IO) {
                        val subscriptionDto: SubscriptionDto =
                            analyzeServiceProvider(serviceProvider, addressToMessages)
                        progressService.setProgress(
                            appUserId,
                            googleAccountSubject,
                            AnalysisProgressStatusDto(
                                AnalysisStatusType.SERVICE_PROVIDER_ANALYSIS_COMPLETED,
                                mapOf("serviceProviderDisplayName" to serviceProvider.displayName)
                            )
                        )
                        subscriptionDto
                    }
                }.awaitAll().filterNotNull()
            }

            progressService.setProgress(
                appUserId,
                googleAccountSubject,
                AnalysisProgressStatusDto(AnalysisStatusType.COMPLETED)
            )

            return subscriptions
        } catch (e: Exception) {
            logger.error(e) { "Error searching account $googleAccountSubject: ${e.message}" }
            return emptyList()
        }
    }

    fun analyzeServiceProvider(
        serviceProvider: ServiceProvider,
        addressToMessages: Map<String, List<GmailMessage>>
    ): SubscriptionDto {
        logger.debug { "[analyzeServiceProvider]" }

        val updatedServiceProvider: ServiceProvider =
            serviceProviderService.updateEmailDetectionRules(serviceProvider, addressToMessages)

        val registeredSince: Instant? = computeRegisteredSince()
        val paidSinceResult: PaidSinceDto = computePaidSince(updatedServiceProvider, addressToMessages)
        val hasSubscribedNewsletterOrAd: Boolean = false
        return SubscriptionDto(
            serviceProviderId = updatedServiceProvider.requiredId,
            registeredSince = registeredSince,
            hasSubscribedNewsletterOrAd = hasSubscribedNewsletterOrAd,
            paidSince = paidSinceResult.paidSince,
            isNotSureIfPaymentIsOngoing = paidSinceResult.isNotSureIfPaymentIsOngoing,
        )
    }

    fun computeRegisteredSince(
//        serviceProvider: ServiceProvider,
//        addressToMessages: Map<EmailSource, List<GmailMessage>>
    ): Instant? {
        return null
    }

    fun computePaidSince(
        serviceProvider: ServiceProvider,
        addressToMessages: Map<String, List<GmailMessage>>
    ): PaidSinceDto {
        if (!(serviceProvider.isEmailDetectionRuleAvailable())) {
            return PaidSinceDto(null)
        }

        val emailSources: MutableList<EmailSource> = serviceProvider.emailSources

        val emailSourceToMessages: Map<EmailSource, List<GmailMessage>> =
            emailSources.mapNotNull { emailSource ->
                val messages = addressToMessages[emailSource.targetAddress]
                messages?.let { emailSource to messages }
            }.toMap()

        if (emailSourceToMessages.isEmpty()) return PaidSinceDto(null)

        var latestPaymentStartMessage: GmailMessage? = null

        if (serviceProvider.isPaymentStartRulePresent()) { // Service sends payment start message
            latestPaymentStartMessage = getLatestPaymentStartMessage(serviceProvider, emailSourceToMessages)

            if (latestPaymentStartMessage != null && serviceProvider.isPaymentCancelRulePresent()) {
                // User got payment start message and service sends payment termination message

                val latestCancel = getLatestPaymentCancelMessage(serviceProvider, emailSourceToMessages)

                if (latestCancel == null || latestPaymentStartMessage.internalDate.isAfter(latestCancel.internalDate)) {
                    // User got no termination message after payment start message
                    return PaidSinceDto(latestPaymentStartMessage.internalDate)
                }
            }
        }

        // Service doesn't send termination message
        if (serviceProvider.isMonthlyPaymentRulePresent()) { // Service sends paid state indicator message
            val oldestConsecutive = getOldestConsecutivePaymentStartMessage(serviceProvider, emailSourceToMessages)
            if (oldestConsecutive != null) {
                return PaidSinceDto(oldestConsecutive.internalDate)
            }
        }

        // Service doesn't send termination message nor paid state indicator message
        return latestPaymentStartMessage?.let { PaidSinceDto(it.internalDate, true) }
            ?: PaidSinceDto(null)
    }

    fun getLatestPaymentStartMessage(
        serviceProvider: ServiceProvider,
        emailSourceToMessages: Map<EmailSource, List<GmailMessage>>
    ): GmailMessage? {

        return emailSourceToMessages.mapNotNull { (emailSource, messages) ->
            emailSource.paymentStartRule?.let {
                matchFirstMessage(messages, it)
            }
        }.minByOrNull { it.internalDate }
    }

    fun getLatestPaymentCancelMessage(
        serviceProvider: ServiceProvider,
        emailSourceToMessages: Map<EmailSource, List<GmailMessage>>
    ): GmailMessage? {

        return emailSourceToMessages.mapNotNull { (emailSource, messages) ->
            emailSource.paymentCancelRule?.let {
                matchFirstMessage(messages, it)
            }
        }.minByOrNull { it.internalDate }
    }


    fun getOldestConsecutivePaymentStartMessage(
        serviceProvider: ServiceProvider,
        emailSourceToMessages: Map<EmailSource, List<GmailMessage>>
    ): GmailMessage? {

        return emailSourceToMessages.mapNotNull { (emailSource, messages) ->
            emailSource.monthlyPaymentRule?.let {
                getConsecutivePaymentStartMessage(it, messages)
            }
        }.minByOrNull { it.internalDate }
    }

    fun getConsecutivePaymentStartMessage(
        monthlyPaymentRule: EmailDetectionRule,
        messages: List<GmailMessage>
    ): GmailMessage? {
        val monthlyPaymentMessages: List<GmailMessage> =
            messages
                .mapNotNull { message ->
                    val isMatched =
                        matchMessageToEvent(message, monthlyPaymentRule)
                    if (isMatched) {
                        message
                    } else null
                }
                .sortedByDescending { it.internalDate }

        if (monthlyPaymentMessages.isEmpty()) {
            return null
        }

        val latestMessage = monthlyPaymentMessages.first()

        if (isBeforeLastMonth(latestMessage.internalDate)) {
            return null
        }
        var consecutivePaymentStartMessage: GmailMessage = latestMessage

        for (i in 0 until monthlyPaymentMessages.size - 1) {
            val current = monthlyPaymentMessages[i]
            val older = monthlyPaymentMessages[i + 1]

            if (isBeforeLastMonth(older.internalDate, current.internalDate)) {
                break
            }
            consecutivePaymentStartMessage = older
        }
        return consecutivePaymentStartMessage
    }

    private fun matchFirstMessage(messages: List<GmailMessage>, rule: EmailDetectionRule): GmailMessage? {
        for (message in messages) {
            val isMatched = matchMessageToEvent(message, rule)
            if (isMatched) {
                return message
            }
        }
        return null
    }

    private fun matchMessageToEvent(message: GmailMessage, rule: EmailDetectionRule): Boolean {
        val subjectMatch: Boolean = matchRegexOrKeyword(message.subject, rule.subjectRegex, rule.subjectKeywords)
        val snippetMatch: Boolean = matchRegexOrKeyword(message.snippet, rule.snippetRegex, rule.snippetKeywords)
        return subjectMatch && snippetMatch
    }

    private fun matchRegexOrKeyword(target: String, regex: String?, keywords: List<String>): Boolean {
        if (!regex.isNullOrEmpty()) {
            regex.let {
                if (it.toRegex(RegexOption.IGNORE_CASE).containsMatchIn(target)) {
                    return true
                }
            }
        }
        return keywords.any { target.contains(it, ignoreCase = true) }
    }


    private fun isBeforeLastMonth(target: Instant, before: Instant = Instant.now()): Boolean {
        val daysSinceLastPayment = ChronoUnit.DAYS.between(target, before)
        return daysSinceLastPayment > MAX_GAP_DAYS
    }
}

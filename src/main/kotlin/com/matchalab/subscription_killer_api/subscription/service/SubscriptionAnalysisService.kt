package com.matchalab.subscription_killer_api.subscription.service

import com.matchalab.subscription_killer_api.domain.GoogleAccount
import com.matchalab.subscription_killer_api.gmail.MessageFetchPlan
import com.matchalab.subscription_killer_api.repository.GoogleAccountRepository
import com.matchalab.subscription_killer_api.service.AppUserService
import com.matchalab.subscription_killer_api.subscription.*
import com.matchalab.subscription_killer_api.subscription.progress.AnalysisProgressStatus
import com.matchalab.subscription_killer_api.subscription.progress.ServiceProviderAnalysisProgressStatus
import com.matchalab.subscription_killer_api.subscription.progress.service.ProgressService
import com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter.GmailClientAdapter
import com.matchalab.subscription_killer_api.subscription.service.gmailclientfactory.GmailClientFactory
import com.matchalab.subscription_killer_api.utils.DateTimeUtils
import com.matchalab.subscription_killer_api.utils.observe
import com.matchalab.subscription_killer_api.utils.observeSuspend
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.annotation.Observed
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

typealias GeneralProgressCallback = (AnalysisProgressStatus) -> Unit
typealias ServiceProviderProgressCallback = (UUID, ServiceProviderAnalysisProgressStatus) -> Unit

@ConfigurationProperties(prefix = "app.mail")
data class MailProperties(val analysisMonths: Long)

@Service
class SubscriptionAnalysisService(
    private val googleAccountRepository: GoogleAccountRepository,
    private val appUserService: AppUserService,
    private val serviceProviderService: ServiceProviderService,
    private val clientFactory: GmailClientFactory,
    private val mailProperties: MailProperties,
    private val progressService: ProgressService,
    private val observationRegistry: ObservationRegistry,
) {

    val after: Instant = DateTimeUtils.minusMonthsFromInstant(Instant.now(), mailProperties.analysisMonths)
    val MAX_GAP_DAYS: Int = 45

    data class PaidSinceDto(
        val paidSince: Instant?,
        val isNotSureIfPaymentIsOngoing: Boolean = false,
    )

    data class SubscriptionDto(
        val serviceProviderId: UUID,
        var registeredSince: Instant?,
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

        progressService.setProgress(
            appUserId,
            googleAccountSubjects.first(),
            AnalysisProgressStatus.COMPLETED
        )

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

    @Observed
    suspend fun analyzeSingleGoogleAccount(appUserId: UUID, googleAccountSubject: String): List<SubscriptionDto> {

        val parent = observationRegistry.currentObservation

        return observationRegistry.observeSuspend(
            "analysis.account",
            parent,
            "googleAccount.subject" to googleAccountSubject
        ) {
            try {

                fun setProgress(status: AnalysisProgressStatus) {
                    progressService.setProgress(
                        appUserId,
                        googleAccountSubject,
                        status
                    )
                }

                fun setServiceProviderProgress(serviceProviderId: UUID, status: ServiceProviderAnalysisProgressStatus) {
                    progressService.setServiceProviderProgress(
                        appUserId,
                        googleAccountSubject,
                        serviceProviderId,
                        status
                    )
                }

                logger.debug { "\n\uD83D\uDE80 | [analyzeSingleGoogleAccount] googleAccountSubject: $googleAccountSubject" }

                // List Gmail Messages
                val gmailClientAdapter: GmailClientAdapter =
                    clientFactory.createAdapter(googleAccountSubject)
                val afterPart: String = "after:${after.epochSecond}"

                val allServiceProviders: List<ServiceProvider> =
                    serviceProviderService.findAllWithEmailSourcesAndAliases()

                val allEmailAddressesAndAliasNames: List<String> = allServiceProviders.flatMap {
                    it.emailSearchAddresses + (it.emailSearchAliasNames?.values ?: emptyList())
                }

                if (allEmailAddressesAndAliasNames.isEmpty()) {
                    return@observeSuspend emptyList()
                }

                val fromPart = allEmailAddressesAndAliasNames.joinToString(separator = " OR ") {
                    "from:\"$it\""
                }
                val listMessageQuery = String.format("%s (%s)", afterPart, fromPart)
                val allMessageIds: List<String> = gmailClientAdapter.listMessageIds(listMessageQuery)

                logger.debug {
                    "🔊 | [analyzeSingleGoogleAccount] gmailClientAdapter.listMessageIds() returned ${allMessageIds.size} messageIds"
                }
                val allMessages: List<GmailMessage> =
                    gmailClientAdapter.getMessages(allMessageIds, MessageFetchPlan.INTERNAL_DATE_SNIPPET_FROM_SUBJECT)

                logger.debug {
                    "🔊 | [analyzeSingleGoogleAccount] gmailClientAdapter.getMessages() returned ${allMessages.size} messages:\n${
                        allMessages.map { it.senderEmail }.joinToString(", ") { it }
                    }"
                }

                setProgress(AnalysisProgressStatus.EMAIL_FETCHED)

                // Add New Email Addresses identified from aliasNames
                // @TODO: optimize
                serviceProviderService.addEmailSourcesFromMessages(allMessages)

                // Analyze Subscription Status
                val uniqueAddresses = allMessages.map { it.senderEmail }.distinct()

                logger.debug {
                    "🔊 | [analyzeSingleGoogleAccount] uniqueAddresses:\n${
                        uniqueAddresses.joinToString("\n") { it }
                    }"
                }

                val serviceProviders =
                    serviceProviderService.findByActiveEmailAddressesInWithEmailSources(uniqueAddresses)

                val subscriptions: List<SubscriptionDto> = coroutineScope {

                    val registeredSinceMapDeferred =
                        async(Dispatchers.IO) {
                            batchComputeRegisteredSince(gmailClientAdapter, serviceProviders)
                        }

                    val subscriptionsDeferred =
                        async(Dispatchers.IO) {
                            computeSubscriptions(
                                serviceProviders,
                                allMessages,
                                ::setServiceProviderProgress
                            )
                        }
                    val registeredSinceMap = registeredSinceMapDeferred.await()
                    val computedSubscriptions = subscriptionsDeferred.await()

                    logger.debug {
                        "🔊  [analyzeSingleGoogleAccount] registeredSinceMap:\n${
                            registeredSinceMap.entries.joinToString(
                                "\n"
                            ) { (key, value) -> "  $key -> $value" }
                        }"
                    }
                    logger.debug {
                        "🔊  [analyzeSingleGoogleAccount] computedSubscriptions:\n${
                            computedSubscriptions.joinToString("\n") { "${serviceProviders.find { sp -> sp.id == it.serviceProviderId }?.displayName} ${it.registeredSince} ${it.paidSince}" }
                        }"
                    }

                    computedSubscriptions.forEach { it.registeredSince = registeredSinceMap[it.serviceProviderId] }

                    computedSubscriptions
                }

                setProgress(
                    AnalysisProgressStatus.EMAIL_ACCOUNT_ANALYSIS_COMPLETED
                )

                logger.debug {
                    "🔊\t[analyzeSingleGoogleAccount] subscriptions:\n${
                        subscriptions.joinToString("\n") { "${serviceProviders.find { sp -> sp.id == it.serviceProviderId }} ${it.registeredSince} ${it.paidSince}" }
                    }"
                }

                subscriptions

            } catch (e: Exception) {
                logger.error(e) { "Error searching account $googleAccountSubject: ${e.message}" }
                emptyList()
            }
        }
    }

    private suspend fun computeSubscriptions(
        serviceProviders: List<ServiceProvider>,
        allMessages: List<GmailMessage>,
        setServiceProviderProgress: ServiceProviderProgressCallback
    ): List<SubscriptionDto> {

        logger.debug {
            "🔊 | [analyzeSingleGoogleAccount] serviceProviders:\n${
                serviceProviders.joinToString("\n") {
                    "[${it.displayName}] emailSource.targetAddress: ${it.emailSources.joinToString(", ") { emailSource -> emailSource.targetAddress }}"
                }
            }"
        }

        val addressToServiceProvider = serviceProviders.flatMap { serviceProvider ->
            serviceProvider.emailSources.map { it.targetAddress to serviceProvider }
        }.toMap()

        logger.debug {
            "🔊 | [analyzeSingleGoogleAccount] all SenderEmails:\n${
                allMessages.map { it.senderEmail }
                    .distinct()
                    .joinToString(",\t")
            }"
        }

        logger.debug {
            "🔊 | [analyzeSingleGoogleAccount] addressToServiceProvider:\n${
                addressToServiceProvider.entries.joinToString(
                    "\n"
                ) { (key, value) -> "  $key -> $value" }
            }"
        }

        val serviceProviderToAddressToMessages = allMessages
            .mapNotNull { message ->
                addressToServiceProvider[message.senderEmail]?.let {
                    (it to message)
                }
            }.groupBy({ it.first }, { it.second })
            .mapValues { (_, messages) ->
                messages.groupBy { it.senderEmail }
            }
        logger.debug {
            "🔊 | [analyzeSingleGoogleAccount] Analyzing ${serviceProviderToAddressToMessages.keys.size} serviceProvider(s): ${serviceProviderToAddressToMessages.keys.map { it.displayName }}"
        }

        val subscriptions: List<SubscriptionDto> =
            coroutineScope {

                val subscriptions: List<SubscriptionDto> =
                    serviceProviderToAddressToMessages.mapNotNull { (serviceProvider, addressToMessages) ->
                        async(Dispatchers.IO) {
                            setServiceProviderProgress(
                                serviceProvider.id!!,
                                ServiceProviderAnalysisProgressStatus.STARTED
                            )
                            val subscriptionDto: SubscriptionDto =
                                analyzeServiceProvider(serviceProvider, addressToMessages)
                            setServiceProviderProgress(
                                serviceProvider.id!!,
                                ServiceProviderAnalysisProgressStatus.COMPLETED
                            )
                            subscriptionDto
                        }
                    }.awaitAll()
                subscriptions
            }

        return subscriptions
    }

    fun analyzeServiceProvider(
        serviceProvider: ServiceProvider,
        addressToMessages: Map<String, List<GmailMessage>>
    ): SubscriptionDto {

        val parent = observationRegistry.currentObservation

        return observationRegistry.observe(
            "analysis.serviceProvider",
            parent,
            "serviceProvider.displayName" to serviceProvider.displayName
        ) {

            logger.debug { "\uD83D\uDE80 | [analyzeServiceProvider] displayName=${serviceProvider.displayName}" }
//            val parent = observationRegistry.currentObservation

//            val updatedServiceProvider: ServiceProvider = observationRegistry.observe(
//                "serviceProvider.updateRules",
//                parent
//            ) {
            val updatedServiceProvider: ServiceProvider =
                serviceProviderService.updateEmailDetectionRules(serviceProvider, addressToMessages)
//            }

            val paidSinceResult: PaidSinceDto = computePaidSince(updatedServiceProvider, addressToMessages)
            val hasSubscribedNewsletterOrAd: Boolean = false

            SubscriptionDto(
                serviceProviderId = updatedServiceProvider.requiredId,
                registeredSince = null,
                hasSubscribedNewsletterOrAd = hasSubscribedNewsletterOrAd,
                paidSince = paidSinceResult.paidSince,
                isNotSureIfPaymentIsOngoing = paidSinceResult.isNotSureIfPaymentIsOngoing,
            )
        }
    }

    private suspend fun batchComputeRegisteredSince(
        gmailClientAdapter: GmailClientAdapter,
        serviceProviders: List<ServiceProvider>
    ): Map<UUID, Instant?> {

        logger.debug {
            "🔊  [batchComputeRegisteredSince] Start computing registeredSince for ${serviceProviders.size} services"
        }

        val serviceProviderIdToFirstMessageIds: Map<UUID, String> = serviceProviders.mapNotNull { serviceProvider ->
            val firstMessageId = gmailClientAdapter.getFirstMessageId(serviceProvider.emailSearchAddresses)
            firstMessageId?.let {
                serviceProvider.id!! to it
            }
        }.toMap()

        logger.debug {
            "🔊  [batchComputeRegisteredSince] serviceProviderIdToFirstMessageIds:${
                serviceProviderIdToFirstMessageIds.entries.joinToString { "\n\t${serviceProviders.find { sp -> sp.id == it.key }?.displayName}: ${it.value}" }
            }"
        }

        val messages: List<GmailMessage> = gmailClientAdapter.getMessages(
            serviceProviderIdToFirstMessageIds.values.toList(),
            MessageFetchPlan.INTERNAL_DATE_SNIPPET_FROM_SUBJECT
        )

        logger.debug {
            "🔊  [batchComputeRegisteredSince] messages:${
                messages.joinToString { "\n\tid: ${it.id}\tinternalDate: ${it.internalDate}" }
            }"
        }

        val messageIdToInternalDate = messages.associate { it.id to it.internalDate }

        logger.debug {
            "🔊  [batchComputeRegisteredSince] messageIdToInternalDate:${
                messageIdToInternalDate.entries.joinToString { "\n\t${it.key}: ${it.value}" }
            }"
        }

        val serviceProviderIdToRegisteredSince: Map<UUID, Instant?> =
            serviceProviderIdToFirstMessageIds.mapValues { (_, messageId) ->
                messageIdToInternalDate[messageId]
            }.filterValues { it != null }

        return serviceProviderIdToRegisteredSince
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

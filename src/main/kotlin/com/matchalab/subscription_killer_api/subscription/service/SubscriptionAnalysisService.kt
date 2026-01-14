package com.matchalab.subscription_killer_api.subscription.service

import com.matchalab.subscription_killer_api.domain.GoogleAccount
import com.matchalab.subscription_killer_api.gmail.MessageFetchPlan
import com.matchalab.subscription_killer_api.repository.GoogleAccountRepository
import com.matchalab.subscription_killer_api.service.AppUserService
import com.matchalab.subscription_killer_api.subscription.*
import com.matchalab.subscription_killer_api.subscription.config.MailProperties
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
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*


private val logger = KotlinLogging.logger {}

typealias GeneralProgressCallback = (AnalysisProgressStatus) -> Unit
typealias ServiceProviderProgressCallback = (UUID, ServiceProviderAnalysisProgressStatus) -> Unit

@Service
class SubscriptionAnalysisService(
    private val googleAccountRepository: GoogleAccountRepository,
    private val appUserService: AppUserService,
    private val serviceProviderService: ServiceProviderService,
    private val clientFactory: GmailClientFactory,
    private val mailProperties: MailProperties,
    private val progressService: ProgressService,
    private val observationRegistry: ObservationRegistry,
    private val subscriptionService: SubscriptionService,
) {

    val after: Instant = DateTimeUtils.minusMonthsFromInstant(Instant.now(), mailProperties.analysisMonths)
    val MAX_GAP_DAYS: Int = 45

    data class SubscribedSinceDto(
        val subscribedSince: Instant?,
        val isNotSureIfSubscriptionIsOngoing: Boolean = false,
    )

    data class SubscriptionDto(
        val serviceProviderId: UUID,
        var registeredSince: Instant?,
        val subscribedSince: Instant?,
        val isNotSureIfSubscriptionIsOngoing: Boolean,
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
        subscriptionDtos.forEach {
            val subscription: Subscription =
                subscriptionService.findByGoogleAccountAndServiceProviderIdOrCreate(googleAccount, it.serviceProviderId)

            subscription.registeredSince = it.registeredSince
            subscription.subscribedSince = it.subscribedSince
            subscription.isNotSureIfSubscriptionIsOngoing = it.isNotSureIfSubscriptionIsOngoing
            subscription.hasSubscribedNewsletterOrAd = it.hasSubscribedNewsletterOrAd

            subscriptionService.save(subscription)
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
                            computedSubscriptions.joinToString("\n") { "${serviceProviders.find { sp -> sp.id == it.serviceProviderId }?.displayName} ${it.registeredSince} ${it.subscribedSince}" }
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
                        subscriptions.joinToString("\n") { "${serviceProviders.find { sp -> sp.id == it.serviceProviderId }} ${it.registeredSince} ${it.subscribedSince}" }
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

//        logger.debug {
//            "🔊 | [analyzeSingleGoogleAccount] allMessages:\n${
//                allMessages.joinToString(",\n") { "senderEmail: ${it.senderEmail}, id: ${it.id}" }
//            }"
//        }
//
//        logger.debug {
//            "🔊 | [analyzeSingleGoogleAccount] serviceProviders:\n${
//                serviceProviders.joinToString("\n") {
//                    "[${it.displayName}] emailSource.targetAddress: ${it.emailSources.joinToString(", ") { emailSource -> emailSource.targetAddress }}"
//                }
//            }"
//        }

        val addressToServiceProvider = serviceProviders.flatMap { serviceProvider ->
            serviceProvider.emailSources.map { it.targetAddress to serviceProvider }
        }.toMap()

//        logger.debug {
//            "🔊 | [analyzeSingleGoogleAccount] all SenderEmails:\n${
//                allMessages.map { it.senderEmail }
//                    .distinct()
//                    .joinToString(",\t")
//            }"
//        }
//
//        logger.debug {
//            "🔊 | [analyzeSingleGoogleAccount] addressToServiceProvider:\n${
//                addressToServiceProvider.entries.joinToString(
//                    "\n"
//                ) { (key, value) -> "  $key -> $value" }
//            }"
//        }

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

            logger.debug { "[analyzeServiceProvider]  \uD83D\uDE80 displayName: ${serviceProvider.displayName}\n\t${addressToMessages.entries.joinToString { "${it.key}: ${it.value}" }}" }

            val updatedServiceProvider: ServiceProvider =
                serviceProviderService.updateEmailDetectionRules(serviceProvider, addressToMessages)
//            }

            val subscribedSinceResult: SubscribedSinceDto =
                computeSubscribedSince(updatedServiceProvider, addressToMessages)
            val hasSubscribedNewsletterOrAd: Boolean = false

            SubscriptionDto(
                serviceProviderId = updatedServiceProvider.requiredId,
                registeredSince = null,
                hasSubscribedNewsletterOrAd = hasSubscribedNewsletterOrAd,
                subscribedSince = subscribedSinceResult.subscribedSince,
                isNotSureIfSubscriptionIsOngoing = subscribedSinceResult.isNotSureIfSubscriptionIsOngoing,
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

    fun computeSubscribedSince(
        serviceProvider: ServiceProvider,
        addressToMessages: Map<String, List<GmailMessage>>
    ): SubscribedSinceDto {

        var subscribedSinceDto: SubscribedSinceDto = SubscribedSinceDto(null)
        var latestStartDay: Instant? = null
        var latestCancelDay: Instant? = null
        var latestMonthlyPayment: Instant? = null

        if (!(serviceProvider.isEmailDetectionRuleAvailable())) {
            return subscribedSinceDto
        }

        // StartRule or MonthlyPayment Rule Exists.

        val emailSources: MutableList<EmailSource> = serviceProvider.emailSources

        val emailSourceToMessages: Map<EmailSource, List<GmailMessage>> =
            emailSources.mapNotNull { emailSource ->
                val messages = addressToMessages[emailSource.targetAddress]
                messages?.let { emailSource to messages }
            }.toMap()

        if (emailSourceToMessages.isEmpty()) return subscribedSinceDto

        if (!(serviceProvider.isEmailDetectionRuleComplete())) {
            // Only StartRule Exists

            latestStartDay = getLatestSubscriptionStartMessage(serviceProvider, emailSourceToMessages)?.internalDate
            return SubscribedSinceDto(latestStartDay, true)
        }

        // One of StartRule+CancelRule or MonthlyPayment Rule Exists.

        if (serviceProvider.isSubscriptionStartRulePresent() && serviceProvider.isSubscriptionCancelRulePresent()) {
            // StartRule+CancelRule Exists.

            latestStartDay = getLatestSubscriptionStartMessage(serviceProvider, emailSourceToMessages)?.internalDate
            latestCancelDay = getLatestSubscriptionCancelMessage(serviceProvider, emailSourceToMessages)?.internalDate

            if ((latestStartDay != null) && ((latestCancelDay == null) || (latestStartDay.isAfter(latestCancelDay)))) {
                // Has Start Message. Has No Cancel Message After Start Message.
                return SubscribedSinceDto(latestStartDay)
            }
            // No Start Message after Last Cancel Message.
            return SubscribedSinceDto(null)
        }

        // StartRule+CancelRule Doesn't Exist. MonthlyPayment Rule Exists.
        val oldestConsecutive =
            getOldestFirstOfConsecutiveMonthlySubscriptionMessage(serviceProvider, emailSourceToMessages)
        if (oldestConsecutive != null) {
            // No Monthly Payment Message.
            return SubscribedSinceDto(oldestConsecutive.internalDate)
        }
        // No Monthly Payment Message.
        return SubscribedSinceDto(null)
    }

    fun getLatestSubscriptionStartMessage(
        serviceProvider: ServiceProvider,
        emailSourceToMessages: Map<EmailSource, List<GmailMessage>>
    ): GmailMessage? {

        return emailSourceToMessages.mapNotNull { (emailSource, messages) ->
            emailSource.paymentStartRule?.let {
                matchLastMessage(messages, it)
            }
        }.maxByOrNull { it.internalDate }
    }

    fun getLatestSubscriptionCancelMessage(
        serviceProvider: ServiceProvider,
        emailSourceToMessages: Map<EmailSource, List<GmailMessage>>
    ): GmailMessage? {

        return emailSourceToMessages.mapNotNull { (emailSource, messages) ->
            emailSource.paymentCancelRule?.let {
                matchLastMessage(messages, it)
            }
        }.maxByOrNull { it.internalDate }
    }

    fun getOldestFirstOfConsecutiveMonthlySubscriptionMessage(
        serviceProvider: ServiceProvider,
        emailSourceToMessages: Map<EmailSource, List<GmailMessage>>
    ): GmailMessage? {

        return emailSourceToMessages.mapNotNull { (emailSource, messages) ->
            emailSource.monthlyPaymentRule?.let {
                getFirstOfConsecutiveMonthlySubscriptionMessage(it, messages)
            }
        }.minByOrNull { it.internalDate }
    }

    fun getFirstOfConsecutiveMonthlySubscriptionMessage(
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
        var consecutiveSubscriptionStartMessage: GmailMessage = latestMessage

        for (i in 0 until monthlyPaymentMessages.size - 1) {
            val current = monthlyPaymentMessages[i]
            val older = monthlyPaymentMessages[i + 1]

            if (isBeforeLastMonth(older.internalDate, current.internalDate)) {
                break
            }
            consecutiveSubscriptionStartMessage = older
        }
        return consecutiveSubscriptionStartMessage
    }

    private fun matchLastMessage(messages: List<GmailMessage>, rule: EmailDetectionRule): GmailMessage? {
        return messages.filter { message ->
            matchMessageToEvent(message, rule)
        }.maxByOrNull { it.internalDate }
    }

    private fun matchMessageToEvent(message: GmailMessage, rule: EmailDetectionRule): Boolean {
        return rule.template.matchMessage(message)
    }

    private fun matchRegex(target: String, regex: String): Boolean {
        return regex.toRegex(RegexOption.IGNORE_CASE).containsMatchIn(target)
    }

    private fun isBeforeLastMonth(target: Instant, before: Instant = Instant.now()): Boolean {
        val daysSinceLastPayment = ChronoUnit.DAYS.between(target, before)
        return daysSinceLastPayment > MAX_GAP_DAYS
    }
}

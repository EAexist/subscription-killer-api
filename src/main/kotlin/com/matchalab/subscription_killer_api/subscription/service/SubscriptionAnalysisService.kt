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
import com.matchalab.subscription_killer_api.utils.observeSuspend
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.kotlin.asContextElement
import io.micrometer.observation.ObservationRegistry
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
    private val emailDetectionRuleService: EmailDetectionRuleService,
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
        observationRegistry.observeSuspend(
            "analyze",
        ) {

            val googleAccountSubjects: List<String> = appUserService.findGoogleAccountSubjectsByAppUserId(appUserId)

            // Fetch Gmail messages in parallel for all accounts
            val googleSubjectToMessages: Map<String, List<GmailMessage>> = coroutineScope {
                googleAccountSubjects.map { subject ->
                    async(Dispatchers.IO) {
                        subject to fetchGmailMessages(appUserId, subject)
                    }
                }
            }.awaitAll().toMap()

            // @TODO: optimize
            val allMessages = googleSubjectToMessages.values.flatten()
            serviceProviderService.addEmailSourcesFromMessages(allMessages)

            val allServiceProviders: List<ServiceProvider> =
                serviceProviderService.findAllWithEmailSourcesAndAliases()

            val allEmailSources: List<EmailSource> = allServiceProviders.flatMap { it.emailSources }

            val emailSourceToMessages: Map<EmailSource, List<GmailMessage>> =
                allEmailSources.associateWith { emailSource ->
                    allMessages.filter { it.senderEmail == emailSource.targetAddress }
                }

            val serviceProviderToEmailSourceToMessages: Map<ServiceProvider, Map<EmailSource, List<GmailMessage>>> =
                emailSourceToMessages
                    .mapNotNull { (emailSource, messages) ->
                        emailSource.serviceProvider?.let { serviceProvider ->
                            serviceProvider to (emailSource to messages)
                        }
                    }.groupBy({ it.first }, { it.second })
                    .mapValues { (_, emailSourceToMessagesList) ->
                        emailSourceToMessagesList.associate { (emailSource, messages) -> emailSource to messages }
                    }

            for (provider in serviceProviderToEmailSourceToMessages.keys) {
                progressService.setServiceProviderProgress(
                    appUserId,
                    googleAccountSubjects.first(),
                    provider.id!!,
                    ServiceProviderAnalysisProgressStatus.STARTED
                )
            }

            // Filter EmailSources that rule isn't complete [Async]
            val emailSourceToMessagesToUpdate: Map<EmailSource, List<GmailMessage>> =
                serviceProviderToEmailSourceToMessages.filter { !it.key.isEmailDetectionRuleComplete() }
                    .values.flatMap { it.entries }
                    .flatMap { (source, messages) -> messages.map { source to it } }
                    .groupBy({ it.first }, { it.second })

            // Start both async operations in parallel
            val (emailDetectionRulesDeferred, registeredSinceDeferred) = coroutineScope {
                // Update EmailDetectionRules [Async]
                val emailDetectionRules = async(Dispatchers.IO) {
                    emailDetectionRuleService.updateEmailDetectionRules(emailSourceToMessagesToUpdate)
                }

                // Compute registeredSince for all accounts [Async]
                val registeredSince = googleSubjectToMessages.map { (subject, messages) ->
                    async(Dispatchers.IO + observationRegistry.asContextElement()) {
                        val uniqueAddresses = messages.map { it.senderEmail }.distinct()

                        logger.debug {
                            "🔊 | [analyze] uniqueAddresses:\n${
                                uniqueAddresses.joinToString("\n") { it }
                            }"
                        }

                        val serviceProviders =
                            serviceProviderService.findByActiveEmailAddressesInWithEmailSources(uniqueAddresses)

                        subject to batchComputeRegisteredSince(
                            subject,
                            serviceProviders
                        )
                    }
                }

                Pair(emailDetectionRules, registeredSince)
            }

            // Wait for email detection rules to complete before computing subscriptions
            emailDetectionRulesDeferred.await()

            val subscriptionDtos: List<SubscriptionDto> = computeSubscription(
                serviceProviderToEmailSourceToMessages
            )

            // Wait for registeredSince calculation to complete and apply to subscriptions
            val googleSubjectToSpIdToRegisteredSince: Map<String, Map<UUID, Instant?>> =
                registeredSinceDeferred.awaitAll().toMap()

            for (subject in googleAccountSubjects) {
                progressService.setProgress(
                    appUserId,
                    subject,
                    AnalysisProgressStatus.EMAIL_ACCOUNT_ANALYSIS_COMPLETED
                )
            }

            // Create a lookup map for efficient SubscriptionDto access by serviceProviderId
            val serviceProviderIdToSubscriptionDto = subscriptionDtos.associateBy { it.serviceProviderId }

            // Process each Google subject and save results
            googleSubjectToMessages.keys.forEach { subject ->
                val spIdToRegisteredSince = googleSubjectToSpIdToRegisteredSince[subject] ?: emptyMap()

                val subjectSubscriptionDtos = spIdToRegisteredSince.mapNotNull { (spId, registeredSince) ->
                    serviceProviderIdToSubscriptionDto[spId]?.apply {
                        this.registeredSince = registeredSince
                    }
                }

                saveAndMapToDto(subject, subjectSubscriptionDtos)
            }

            progressService.setProgress(
                appUserId,
                googleAccountSubjects.first(),
                AnalysisProgressStatus.COMPLETED
            )
        }

    }

    private suspend fun fetchGmailMessages(appUserId: UUID, googleAccountSubject: String): List<GmailMessage> {
        return observationRegistry.observeSuspend(
            "fetch_gmail_messages",
            "google_account.subject" to googleAccountSubject
        ) {
            logger.debug { "\uD83D\uDE80 | [fetchGmailMessages] googleAccountSubject: $googleAccountSubject" }

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
                "🔊 | [fetchGmailMessages] gmailClientAdapter.listMessageIds() returned ${allMessageIds.size} messageIds"
            }
            val allMessages: List<GmailMessage> =
                gmailClientAdapter.getMessages(allMessageIds, MessageFetchPlan.INTERNAL_DATE_SNIPPET_FROM_SUBJECT)

            logger.debug {
                "🔊 | [fetchGmailMessages] gmailClientAdapter.getMessages() returned ${allMessages.size} messages:\n${
                    allMessages.map { it.senderEmail }.joinToString(", ") { it }
                }"
            }

            progressService.setProgress(
                appUserId,
                googleAccountSubject,
                AnalysisProgressStatus.EMAIL_FETCHED
            )

            allMessages
        }
    }

//    private suspend fun processFetchedMessages(
//        allMessages: List<GmailMessage>,
//        allServiceProviders: List<ServiceProvider>
//    ): List<SubscriptionDto> {
//        return observationRegistry.observe(
//            "process_fetched_messages",
//            "google_account.subject" to "temp_subject" // TODO: Fix with proper subject
//        ) {
////            fun setServiceProviderProgress(serviceProviderId: UUID, status: ServiceProviderAnalysisProgressStatus) {
////                progressService.setServiceProviderProgress(
////                    appUserId,
////                    googleAccountSubject,
////                    serviceProviderId,
////                    status
////                )
////            }
//
//            // Add New Email Addresses identified from aliasNames
//            // @TODO: optimize
//            serviceProviderService.addEmailSourcesFromMessages(allMessages)
//
//            val addressToServiceProvider = allServiceProviders.flatMap { serviceProvider ->
//                serviceProvider.emailSources.map { it.targetAddress to serviceProvider }
//            }.toMap()
//
//            val serviceProviderToEmailSourceToMessages = allMessages
//                .mapNotNull { message ->
//                    addressToServiceProvider[message.senderEmail]?.let {
//                        (it to message)
//                    }
//                }.groupBy({ it.first }, { it.second })
//                .mapValues { (_, messages) ->
//                    messages.groupBy { it.senderEmail }
//                }
//
//
//            //        logger.debug {
//            //            "🔊 | [analyzeSingleGoogleAccount] all SenderEmails:\n${
//            //                allMessages.map { it.senderEmail }
//            //                    .distinct()
//            //                    .joinToString(",\t")
//            //            }"
//            //        }
//            //
//            //        logger.debug {
//            //            "🔊 | [analyzeSingleGoogleAccount] addressToServiceProvider:\n${
//            //                addressToServiceProvider.entries.joinToString(
//            //                    "\n"
//            //                ) { (key, value) -> "  $key -> $value" }
//            //            }"
//            //        }
//
//            logger.debug {
//                "🔊 | [analyzeSingleGoogleAccount] Analyzing ${serviceProviderToEmailSourceToMessages.keys.size} serviceProvider(s): ${serviceProviderToEmailSourceToMessages.keys.map { it.displayName }}"
//            }
//
//            emailDetectionRuleService.updateEmailDetectionRules(serviceProviderToEmailSourceToMessages.values.flatMap { it })
//
//            // Analyze Subscription Status
//            val uniqueAddresses = allMessages.map { it.senderEmail }.distinct()
//
//            logger.debug {
//                "🔊 | [processFetchedMessages] uniqueAddresses:\n${
//                    uniqueAddresses.joinToString("\n") { it }
//                }"
//            }
//
//            val serviceProviders =
//                serviceProviderService.findByActiveEmailAddressesInWithEmailSources(uniqueAddresses)
//
//            val subscriptions: List<SubscriptionDto> = computeSubscription(
//                serviceProviderToEmailSourceToMessages
//            )
//
//            logger.debug {
//                "🔊  [processFetchedMessages] computedSubscriptions:\n${
//                    subscriptions.joinToString("\n") { "${serviceProviders.find { sp -> sp.id == it.serviceProviderId }?.displayName} ${it.registeredSince} ${it.subscribedSince}" }
//                }"
//            }
//
//            subscriptions
//        }
//    }


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

    private suspend fun computeSubscription(
        serviceProviderToEmailSourceToMessages: Map<ServiceProvider, Map<EmailSource, List<GmailMessage>>>
    ): List<SubscriptionDto> {

        val subscriptions: List<SubscriptionDto> =
            serviceProviderToEmailSourceToMessages.mapNotNull { (serviceProvider, emailSourceToMessages) ->
                val subscribedSinceResult: SubscribedSinceDto =
                    computeSubscribedSince(serviceProvider, emailSourceToMessages)
                val hasSubscribedNewsletterOrAd = false

                SubscriptionDto(
                    serviceProviderId = serviceProvider.requiredId,
                    registeredSince = null,
                    hasSubscribedNewsletterOrAd = hasSubscribedNewsletterOrAd,
                    subscribedSince = subscribedSinceResult.subscribedSince,
                    isNotSureIfSubscriptionIsOngoing = subscribedSinceResult.isNotSureIfSubscriptionIsOngoing,
                )

//                            setServiceProviderProgress(
//                                serviceProvider.id!!,
//                                ServiceProviderAnalysisProgressStatus.STARTED
//                            )
//                            setServiceProviderProgress(
//                                serviceProvider.id!!,
//                                ServiceProviderAnalysisProgressStatus.COMPLETED
//                            )
            }

        return subscriptions
    }

    private suspend fun batchComputeRegisteredSince(
        googleAccountSubject: String,
        serviceProviders: List<ServiceProvider>
    ): Map<UUID, Instant?> {

        val gmailClientAdapter: GmailClientAdapter =
            clientFactory.createAdapter(googleAccountSubject)

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
        emailSourceToMessages: Map<EmailSource, List<GmailMessage>>
    ): SubscribedSinceDto {

        var subscribedSinceDto: SubscribedSinceDto = SubscribedSinceDto(subscribedSince = null)
        var latestStartDay: Instant?
        var latestCancelDay: Instant?

        if (!(serviceProvider.isEmailDetectionRuleAvailable())) {
            return subscribedSinceDto
        }

        // StartRule or MonthlyPayment Rule Exists.

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

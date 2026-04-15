package com.matchalab.subscription_killer_api.subscription.service

import com.matchalab.subscription_killer_api.gmail.MessageFetchPlan
import com.matchalab.subscription_killer_api.service.AppUserService
import com.matchalab.subscription_killer_api.service.GoogleAccountService
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.subscription.ServiceProvider
import com.matchalab.subscription_killer_api.subscription.config.MailProperties
import com.matchalab.subscription_killer_api.subscription.progress.AnalysisProgressStatus
import com.matchalab.subscription_killer_api.subscription.progress.ServiceProviderAnalysisProgressStatus
import com.matchalab.subscription_killer_api.subscription.progress.service.ProgressService
import com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter.GmailClientAdapter
import com.matchalab.subscription_killer_api.subscription.service.gmailclientfactory.ProxyGmailClientFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.kotlin.asContextElement
import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.annotation.Observed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*


private val logger = KotlinLogging.logger {}

typealias GeneralProgressCallback = (AnalysisProgressStatus) -> Unit
typealias ServiceProviderProgressCallback = (UUID, ServiceProviderAnalysisProgressStatus) -> Unit

@Service
class SubscriptionAnalysisService(
    private val googleAccountService: GoogleAccountService,
    private val appUserService: AppUserService,
    private val serviceProviderService: ServiceProviderService,
    private val clientFactory: ProxyGmailClientFactory,
    private val mailProperties: MailProperties,
    private val progressService: ProgressService,
    private val observationRegistry: ObservationRegistry,
    private val subscriptionService: SubscriptionService,
    private val subscriptionEventRuleService: SubscriptionEventRuleService,
    private val emailSourceService: EmailSourceService,
) {
    suspend fun analyze(appUserId: UUID) {
//        observationRegistry.observeSuspend(
//            "analyze",
//        ) {

        val googleAccountSubjects: List<String> =
            appUserService.findGoogleAccountSubjectsByAppUserId(appUserId)

        // Fetch Gmail messages in parallel for all accounts
        val googleSubjectToMessages: Map<String, List<GmailMessage>> = coroutineScope {
            googleAccountSubjects.map { subject ->
                async(Dispatchers.IO) {
                    subject to fetchGmailMessages(appUserId, subject)
                }
            }.awaitAll()
        }.toMap()

        // @TODO: optimize
        val allMessages = googleSubjectToMessages.values.flatten()
        serviceProviderService.addEmailSourcesFromMessages(allMessages)

        /* @TODO Postpone the matchedMessage Processing to after AI calls */
        val emailSourceIdToUnmatchedMessages = mutableMapOf<UUID, MutableList<GmailMessage>>()
        val googleAccountToServiceProviders = mutableMapOf<String, MutableSet<ServiceProvider>>()

        // 3. Process & Match
        googleSubjectToMessages.forEach { (subject, messages) ->
//                logger.debug {
//                    "🔊 | [googleSubjectToMessages] subject: $subject, messages size: ${messages.size}"
//                }
            messages.forEach { message ->
                val emailSource = emailSourceService.getEmailSource(message)
                    ?: throw IllegalStateException("No active EmailSource found for sender: ${message.senderName}, ${message.senderEmail}, ${message.subject}")
                val event = emailSourceService.matchMessageToEvent(emailSource, message)
                logger.debug {
                    "🔊 | emailSource: ${emailSource.id} message: ${message.subject}. event: $event"
                }
                if (event != null) {
                    subscriptionService.addEvent(
                        subject,
                        emailSource.serviceProvider.id!!,
                        event,
                        message.internalDate
                    )
                } else {
                    emailSourceIdToUnmatchedMessages.getOrPut(emailSource.id!!) { mutableListOf() }
                        .add(message)
                }
            }
        }

        val unMatchedMessageSize = emailSourceIdToUnmatchedMessages.values.sumOf { it.size }

        logger.debug {
            "🔊 | [Cache Hit] New Templates: ${unMatchedMessageSize}/${allMessages.size}"
        }

        for (serviceProviders in googleAccountToServiceProviders.values) {
            for (serviceProvider in serviceProviders) {
                progressService.setServiceProviderProgress(
                    appUserId,
                    googleAccountSubjects.first(),
                    serviceProvider.id!!,
                    ServiceProviderAnalysisProgressStatus.STARTED
                )
            }
        }

        coroutineScope {
            val emailRulesDeferred = async(Dispatchers.IO) {
                subscriptionEventRuleService.updateSubscriptionEventRules(
                    emailSourceIdToUnmatchedMessages
                )
            }

            val googleAccountToProvidersList =
                googleAccountToServiceProviders.map { (subject, providers) ->
                    val googleAccount = googleAccountService.findById(subject)
                    googleAccount to providers.toList()
                }.toMap()

            val newSubscriptions = subscriptionService.update(googleAccountToProvidersList)

            val registeredSinceDeferred =
                newSubscriptions.groupBy { it.googleAccount }.map { (ga, subscriptions) ->
                    async(Dispatchers.IO + observationRegistry.asContextElement()) {
                        subscriptionService.updateRegisteredSince(
                            ga.subject!!,
                            subscriptions.map { it.serviceProvider.id!! })
                    }
                }


            emailRulesDeferred.await()
            registeredSinceDeferred.awaitAll()

            for (googleAccount in googleAccountToProvidersList.keys) {
                googleAccount.analyzedAt = Instant.now()
                progressService.setProgress(
                    appUserId,
                    googleAccount.subject!!,
                    AnalysisProgressStatus.EMAIL_ACCOUNT_ANALYSIS_COMPLETED
                )
            }

            progressService.setProgress(
                appUserId,
                googleAccountSubjects.first(),
                AnalysisProgressStatus.COMPLETED
            )
        }
//        }

    }

    @Observed(name = "fetch_gmail_messages")
    private suspend fun fetchGmailMessages(
        appUserId: UUID,
        googleAccountSubject: String
    ): List<GmailMessage> {
//        return observationRegistry.observeSuspend(
//            "fetch_gmail_messages",
//            "google_account.subject" to googleAccountSubject
//        ) {
        val lastEmailSyncedAt = googleAccountService.getAnalyzeAfter(googleAccountSubject)
        val now = Instant.now()
        val duration = java.time.Duration.between(lastEmailSyncedAt, now)
        val days = duration.toDays()
        val hours = duration.toHours() % 24
        val minutes = duration.toMinutes() % 60
        logger.info { "🚀 Fetching gmail for last ${days}d ${hours}h ${minutes}m" }

        // List Gmail Messages
        val gmailClientAdapter: GmailClientAdapter =
            clientFactory.createAdapter(googleAccountSubject)
        val afterPart = "after:${lastEmailSyncedAt.epochSecond}"

        val allServiceProviders: List<ServiceProvider> =
            serviceProviderService.findAllWithEmailSourcesAndAliases()

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

//            logger.debug {
//                "🔊 | [fetchGmailMessages] gmailClientAdapter.listMessageIds() returned ${allMessageIds.size} messageIds"
//            }
        val allMessages: List<GmailMessage> =
            gmailClientAdapter.getMessages(
                allMessageIds,
                MessageFetchPlan.INTERNAL_DATE_SNIPPET_FROM_SUBJECT
            )

//            logger.debug {
//                "🔊 | [fetchGmailMessages] gmailClientAdapter.getMessages() returned ${allMessages.size} messages:\n${
//                    allMessages.map { it.senderEmail }.joinToString(", ") { it }
//                }"
//            }

        progressService.setProgress(
            appUserId,
            googleAccountSubject,
            AnalysisProgressStatus.EMAIL_FETCHED
        )

        return allMessages
//        }
    }

}
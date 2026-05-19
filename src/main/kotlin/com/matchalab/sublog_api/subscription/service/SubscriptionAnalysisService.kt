package com.matchalab.sublog_api.subscription.service

import com.matchalab.sublog_api.gmail.MessageFetchPlan
import com.matchalab.sublog_api.service.AppUserService
import com.matchalab.sublog_api.service.GoogleAccountService
import com.matchalab.sublog_api.subscription.GmailMessage
import com.matchalab.sublog_api.subscription.ServiceProvider
import com.matchalab.sublog_api.subscription.Subscription
import com.matchalab.sublog_api.subscription.SubscriptionEventType
import com.matchalab.sublog_api.subscription.config.MailProperties
import com.matchalab.sublog_api.subscription.progress.AnalysisProgressStatus
import com.matchalab.sublog_api.subscription.progress.ServiceProviderAnalysisProgressStatus
import com.matchalab.sublog_api.subscription.progress.service.ProgressService
import com.matchalab.sublog_api.subscription.service.gmailclientadapter.GmailClientAdapter
import com.matchalab.sublog_api.subscription.service.gmailclientfactory.ProxyGmailClientFactory
import com.matchalab.sublog_api.utils.DateTimeUtils
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

        val lastEmailSyncedAt = Instant.now()

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
        val googleSubjectToUnmatchedMessageAndEmailSourceId =
            mutableMapOf<String, MutableList<Pair<GmailMessage, UUID>>>()
        val googleAccountToServiceProviderIds = mutableMapOf<String, MutableSet<UUID>>()

        // 3. Process & Match
        googleSubjectToMessages.forEach { (subject, messages) ->
//                logger.debug {
//                    "🔊 | [googleSubjectToMessages] subject: $subject, messages size: ${messages.size}"
//                }
            messages.forEach { message ->
                val emailSource = emailSourceService.getEmailSource(message)
                    ?: return@forEach // getEmailSource can return null (e.g. email address = google-payments@google.com, but sender isn't any service provider registered in our service.

                googleAccountToServiceProviderIds.getOrPut(subject) { mutableSetOf() }
                    .add(emailSource.serviceProvider.id!!)

                val event = emailSourceService.matchMessageToEvent(emailSource, message)
//                logger.debug {
//                    "🔊 | emailSource: ${emailSource.id} message: ${message.subject}. event: $event"
//                }
                if (event != null) {
                    if (event != SubscriptionEventType.NOT_A_SUBSCRIPTION_EMAIL) {
                        subscriptionService.addEvent(
                            subject,
                            emailSource.serviceProvider.id!!,
                            event,
                            message.internalDate
                        )
                    }
                } else {
                    googleSubjectToUnmatchedMessageAndEmailSourceId.getOrPut(subject) { mutableListOf() }
                        .add(
                            Pair(
                                message,
                                emailSource.id!!
                            )
                        )
                }
            }
        }

        val unMatchedMessageSize =
            googleSubjectToUnmatchedMessageAndEmailSourceId.values.sumOf { it.size }

        val emailSourceIdToUnmatchedMessages: Map<UUID, List<GmailMessage>> =
            googleSubjectToUnmatchedMessageAndEmailSourceId.values.flatten().groupBy { it.second }
                .mapValues { it.value.map { it.first }.toMutableList() }

        logger.debug {
            "🔊 | [Cache Hit] New Templates: ${unMatchedMessageSize}/${allMessages.size}"
        }

        coroutineScope {
            val emailRulesDeferred = async {
                subscriptionEventRuleService.updateSubscriptionEventRules(
                    emailSourceIdToUnmatchedMessages
                )
            }

            val googleAccountToProvidersList =
                googleAccountToServiceProviderIds.map { (subject, providers) ->
                    val googleAccount = googleAccountService.findById(subject)
                    googleAccount to providers.toList()
                }.toMap()

            subscriptionService.update(googleAccountToProvidersList)

            val subscriptionIdToRegisteredSinceDeferred =
                googleAccountSubjects.map { subject ->
                    async(Dispatchers.IO + observationRegistry.asContextElement()) {
                        subscriptionService.fetchAllRegisteredSince(subject)
                    }
                }

            for (serviceProviderIds in googleAccountToServiceProviderIds.values) {
                for (serviceProviderId in serviceProviderIds) {
                    progressService.setServiceProviderProgress(
                        appUserId,
                        googleAccountSubjects.first(),
                        serviceProviderId,
                        ServiceProviderAnalysisProgressStatus.STARTED
                    )
                }
            }

            emailRulesDeferred.await()

            for (googleAccount in googleAccountToProvidersList.keys) {
                progressService.setProgress(
                    appUserId,
                    googleAccount.subject!!,
                    AnalysisProgressStatus.EMAIL_ACCOUNT_ANALYSIS_COMPLETED
                )
            }


            val idToEmailSource = emailSourceService.findAllById(
                googleSubjectToUnmatchedMessageAndEmailSourceId.values.flatten().map { it.second })
                .associateBy { it.id }

            googleSubjectToUnmatchedMessageAndEmailSourceId.forEach { (subject, pairs) ->
                pairs.forEach { (message, emailSourceId) ->
                    val emailSource = idToEmailSource[emailSourceId]!!
                    val event = emailSourceService.matchMessageToEvent(emailSource, message)
                    if ((event != null) && (event != SubscriptionEventType.NOT_A_SUBSCRIPTION_EMAIL)) {
                        subscriptionService.addEvent(
                            subject,
                            emailSource.serviceProvider.id!!,
                            event,
                            message.internalDate
                        )
                    }
                }
            }

            for (googleAccount in googleAccountToProvidersList.keys) {
                googleAccount.lastEmailSyncedAt = lastEmailSyncedAt
                googleAccountService.save(googleAccount)
            }

            val subscriptionIdToRegisteredSince =
                subscriptionIdToRegisteredSinceDeferred.awaitAll().flatten().toMap()

            val subscriptions: List<Subscription> =
                subscriptionService.findAllWithDetailsByIds(subscriptionIdToRegisteredSince.keys.toList())


            subscriptions.forEach {
                it.registeredSince = subscriptionIdToRegisteredSince[it.id]
            }

            subscriptionService.saveAll(subscriptions)

            progressService.setProgress(
                appUserId,
                googleAccountSubjects.first(),
                AnalysisProgressStatus.COMPLETED
            )
        }
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
        val lastEmailSyncedAt = googleAccountService.getLastEmailSyncedAt(googleAccountSubject)
        val now = Instant.now()
        val start = lastEmailSyncedAt ?: DateTimeUtils.minusMonthsFromInstant(
            now,
            mailProperties.analysisMonths
        )
        val duration = java.time.Duration.between(start, now)
        val days = duration.toDays()
        val hours = duration.toHours() % 24
        val minutes = duration.toMinutes() % 60
        logger.info { "🚀 Fetching gmail for last ${days}d ${hours}h ${minutes}m" }

        // List Gmail Messages
        val gmailClientAdapter: GmailClientAdapter =
            clientFactory.createAdapter(googleAccountSubject)
        val afterPart = "after:${start.epochSecond}"

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
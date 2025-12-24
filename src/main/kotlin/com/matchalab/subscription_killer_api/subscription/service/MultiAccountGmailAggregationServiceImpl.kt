package com.matchalab.subscription_killer_api.subscription.service

import com.matchalab.subscription_killer_api.repository.AppUserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class MultiAccountGmailAggregationServiceImpl(
        // private val clientFactory: GmailClientFactory,
        private val appUserRepository: AppUserRepository
) : MultiAccountGmailAggregationService {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun getGoogleAccountSubjects(): List<String> {
        val context: SecurityContext = SecurityContextHolder.getContext()
        val authentication: Authentication? = context.getAuthentication()
        if (authentication == null || !authentication.isAuthenticated()) {
            throw AccessDeniedException("User is not authenticated.")
        }
        val appUserId: UUID = UUID.fromString(authentication.name)
        return appUserRepository.findGoogleAccountSubjectsByAppUserId(appUserId)
    }

    // suspend fun listSenders(
    //         after: Instant,
    // ): List<String> = coroutineScope {
    //     val deferredResults =
    //             getGoogleAccountSubjects().map { googleAccountSubject: String ->
    //                 async {
    //                     try {
    //                         val gmailClientAdapter: GmailClientAdapter =
    //                                 clientFactory.createAdapter(googleAccountSubject)

    //                         gmailClientAdapter.listSenders(after)
    //                     } catch (e: Exception) {
    //                         println("Error searching account $googleAccountSubject:
    // ${e.message}")
    //                         emptyList()
    //                     }
    //                 }
    //             }

    //     deferredResults.awaitAll().flatten()
    // }

    // suspend fun listMessagesBySender(
    //         senderEmail: String,
    //         after: Instant,
    // ): List<Message> = coroutineScope {
    //     val deferredResults =
    //             getGoogleAccountSubjects().map { googleAccountSubject: String ->
    //                 async {
    //                     try {
    //                         val gmailClientAdapter: GmailClientAdapter =
    //                                 clientFactory.createAdapter(googleAccountSubject)

    //                         gmailClientAdapter.listMessagesBySender(senderEmail, after)
    //                     } catch (e: Exception) {
    //                         println("Error searching account $googleAccountSubject:
    // ${e.message}")
    //                         emptyList()
    //                     }
    //                 }
    //             }

    //     deferredResults.awaitAll().flatten()
    // }

    // suspend fun listMessagesBySenderAndSubject(
    //         senderEmail: String,
    //         subjectKeyword: String,
    //         after: Instant,
    // ): List<Message> = coroutineScope {
    //     val deferredResults =
    //             getGoogleAccountSubjects().map { googleAccountSubject: String ->
    //                 async {
    //                     try {
    //                         val gmailClientAdapter: GmailClientAdapter =
    //                                 clientFactory.createAdapter(googleAccountSubject)

    //                         gmailClientAdapter.listMessagesBySenderAndSubject(
    //                                 senderEmail,
    //                                 subjectKeyword,
    //                                 after
    //                         )
    //                     } catch (e: Exception) {
    //                         println("Error searching account $googleAccountSubject:
    // ${e.message}")
    //                         emptyList()
    //                     }
    //                 }
    //             }

    //     deferredResults.awaitAll().flatten()
    // }
}
